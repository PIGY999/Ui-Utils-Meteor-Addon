package com.example.addon.commands;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.ChatPacketSender;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GetAllClansCommand extends Command {
    private enum State {
        IDLE,
        WAITING_FOR_GUI,
        CLICKING_INITIAL_ARMOR_STAND,
        SCANNING_PAGE,
        CLICKING_NEXT_PAGE,
        WAITING_FOR_PAGE_UPDATE,
        SETTLING_AFTER_PAGE_UPDATE,
        FINISHED
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int GUI_OPEN_TIMEOUT_TICKS = 100;
    private static final int EMPTY_PAGE_MAX_WAIT_TICKS = 100;
    private static final int NO_ARROW_MAX_WAIT_TICKS = 100;
    private static final int PAGE_UPDATE_MAX_WAIT_TICKS = 100;
    private static final int PAGE_SETTLE_TICKS = 10;
    private static final int ACTION_WAIT_TICKS = 1;
    private static final int HEARTBEAT_TICKS = 20;
    private static final boolean DEBUG_LOGS = true;

    private State state = State.IDLE;
    private int tickCounter = 0;
    private int currentPage = 0;
    private int initialClickWaitTicks = 0;
    private int emptyWaitTicks = 0;
    private int noArrowWaitTicks = 0;
    private int pageUpdateWaitTicks = 0;
    private int pageSettleTicks = 0;
    private int endedAtPage = 0;
    private final List<Integer> armorStandSlots = new ArrayList<>();
    private int armorStandIndex = 0;
    private final Set<String> seenEntries = new HashSet<>();
    private final List<ClanEntry> entries = new ArrayList<>();
    private String startedAtIso = null;
    private String outputPath = null;
    
    private File outputFile = null;
    private String previousPageFingerprint = "";

    public GetAllClansCommand() {
        super("getallclans", "Gets all clan information from /clans pages and writes it to JSON.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            start();
            return SINGLE_SUCCESS;
        });
    }

    private void start() {
        if (state != State.IDLE) {
            error("Already running. Wait for completion.");
            return;
        }

        tickCounter = 0;
        currentPage = 0;
        initialClickWaitTicks = 0;
        emptyWaitTicks = 0;
        noArrowWaitTicks = 0;
        pageUpdateWaitTicks = 0;
        pageSettleTicks = 0;
        endedAtPage = 0;
        armorStandSlots.clear();
        armorStandIndex = 0;
        seenEntries.clear();
        entries.clear();
        startedAtIso = Instant.now().toString();
        initializeOutputFile();
        previousPageFingerprint = "";
        state = State.WAITING_FOR_GUI;

        MeteorClient.EVENT_BUS.subscribe(this);

        info("Starting GetAllClans...");
        info("Sending /clans and waiting for GUI...");
        ChatPacketSender.sendCommand("clans");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (state == State.IDLE) return;

        tickCounter++;

        if (DEBUG_LOGS && tickCounter % HEARTBEAT_TICKS == 0) {
            debug("Heartbeat | state=" + state + " page=" + currentPage + " tickCounter=" + tickCounter
                + " emptyWaitTicks=" + emptyWaitTicks + " armorIndex=" + armorStandIndex + "/" + armorStandSlots.size()
                + " initialClickWaitTicks=" + initialClickWaitTicks
                + " noArrowWaitTicks=" + noArrowWaitTicks
                + " pageUpdateWaitTicks=" + pageUpdateWaitTicks
                + " screen=" + getScreenName());
        }

        switch (state) {
            case WAITING_FOR_GUI -> handleWaitingForGui();
            case CLICKING_INITIAL_ARMOR_STAND -> handleClickingInitialArmorStand();
            case SCANNING_PAGE -> handleScanningPage();
            case CLICKING_NEXT_PAGE -> handleClickingNextPage();
            case WAITING_FOR_PAGE_UPDATE -> handleWaitingForPageUpdate();
            case SETTLING_AFTER_PAGE_UPDATE -> handleSettlingAfterPageUpdate();
            case FINISHED -> finishAndReset();
        }
    }

    private void handleWaitingForGui() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            debug("Detected GenericContainerScreen while waiting for GUI.");
            currentPage = 1;
            tickCounter = 0;
            initialClickWaitTicks = 0;
            emptyWaitTicks = 0;
            noArrowWaitTicks = 0;
            pageUpdateWaitTicks = 0;
            pageSettleTicks = 0;
            armorStandSlots.clear();
            armorStandIndex = 0;
            info("GUI opened. Clicking first armor stand to open clan menu...");
            state = State.CLICKING_INITIAL_ARMOR_STAND;
            return;
        }

        if (tickCounter > GUI_OPEN_TIMEOUT_TICKS) {
            error("Timed out waiting for /clans GUI.");
            endedAtPage = currentPage;
            state = State.FINISHED;
        }
    }

    private void handleClickingInitialArmorStand() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            warning("GUI closed while waiting to click the first armor stand.");
            endedAtPage = 0;
            state = State.FINISHED;
            return;
        }

        if (tickCounter < ACTION_WAIT_TICKS) return;

        int firstArmorStandSlot = findFirstArmorStandSlot(screen);
        if (firstArmorStandSlot == -1) {
            initialClickWaitTicks++;
            if (initialClickWaitTicks == 1) {
                warning("No armor stand found yet in initial menu. Waiting up to 5000ms...");
            }

            if (initialClickWaitTicks >= GUI_OPEN_TIMEOUT_TICKS) {
                error("Could not find any armor stand to open the clan menu within 5000ms.");
                state = State.FINISHED;
            }
            return;
        }

        initialClickWaitTicks = 0;
        previousPageFingerprint = buildPageFingerprint(screen);
        info("Clicking first armor stand at slot " + firstArmorStandSlot + " to open clan pages...");
        clickSlot(screen, firstArmorStandSlot);

        armorStandSlots.clear();
        armorStandIndex = 0;
        emptyWaitTicks = 0;
        noArrowWaitTicks = 0;
        pageUpdateWaitTicks = 0;
        pageSettleTicks = 0;
        tickCounter = 0;
        state = State.WAITING_FOR_PAGE_UPDATE;
    }

    private void handleScanningPage() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            warning("GUI closed while scanning.");
            endedAtPage = currentPage;
            state = State.FINISHED;
            return;
        }

        if (tickCounter < ACTION_WAIT_TICKS) {
            if (DEBUG_LOGS && tickCounter == 1) {
                debug("Scan wait gate active: tickCounter=" + tickCounter + "/" + ACTION_WAIT_TICKS + ".");
            }
            return;
        }

        if (armorStandSlots.isEmpty()) {
            discoverArmorStandSlots(screen);

            if (armorStandSlots.isEmpty()) {
                emptyWaitTicks++;

                if (emptyWaitTicks == 1) {
                    warning("No armor stands found on page " + currentPage + ". Waiting up to 5000ms...");
                }

                if (emptyWaitTicks >= EMPTY_PAGE_MAX_WAIT_TICKS) {
                    if (isNextPageArrowPresent(screen)) {
                        info("Page " + currentPage + " still empty, but arrow exists at slot " + NEXT_PAGE_SLOT + ". Going next page...");
                        saveProgressJson(currentPage);
                        tickCounter = 0;
                        state = State.CLICKING_NEXT_PAGE;
                    } else {
                        warning("Page " + currentPage + " stayed empty for up to 5000ms and no arrow found. Ending.");
                        endedAtPage = currentPage;
                        state = State.FINISHED;
                    }
                }

                if (DEBUG_LOGS && emptyWaitTicks % HEARTBEAT_TICKS == 0) {
                    debug("Still no armor stands on page " + currentPage + " after " + (emptyWaitTicks * 50) + "ms."
                        + " ArrowAt" + NEXT_PAGE_SLOT + "=" + isNextPageArrowPresent(screen));
                }
                return;
            }

            emptyWaitTicks = 0;
            noArrowWaitTicks = 0;
            armorStandIndex = 0;
            info("Found " + armorStandSlots.size() + " armor stands on page " + currentPage + ".");
        }

        if (armorStandIndex < armorStandSlots.size()) {
            while (armorStandIndex < armorStandSlots.size()) {
                int slotId = armorStandSlots.get(armorStandIndex++);
                debug("Logging armor stand index " + armorStandIndex + "/" + armorStandSlots.size() + " at slot " + slotId + ".");
                readAndLogArmorStand(screen, slotId);
            }
        }

        if (isNextPageArrowPresent(screen)) {
            noArrowWaitTicks = 0;
            saveProgressJson(currentPage);
            info("Arrow found at slot " + NEXT_PAGE_SLOT + ". Moving to next page...");
            tickCounter = 0;
            state = State.CLICKING_NEXT_PAGE;
        } else {
            noArrowWaitTicks++;

            if (noArrowWaitTicks == 1) {
                warning("No arrow currently at slot " + NEXT_PAGE_SLOT + ". Waiting up to 5000ms for GUI update...");
            }

            if (DEBUG_LOGS && noArrowWaitTicks % HEARTBEAT_TICKS == 0) {
                debug("Arrow still missing after " + (noArrowWaitTicks * 50) + "ms on page " + currentPage + ".");
            }

            if (noArrowWaitTicks >= NO_ARROW_MAX_WAIT_TICKS) {
                saveProgressJson(currentPage);
                endedAtPage = currentPage;
                info("No arrow at slot " + NEXT_PAGE_SLOT + " after 5000ms. Finished at page " + endedAtPage + ".");
                state = State.FINISHED;
            }
        }
    }

    private void handleClickingNextPage() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            warning("GUI closed while trying to click next page.");
            endedAtPage = currentPage;
            state = State.FINISHED;
            return;
        }

        if (tickCounter < ACTION_WAIT_TICKS) {
            if (DEBUG_LOGS && tickCounter == 1) {
                debug("Next-page wait gate active: tickCounter=" + tickCounter + "/" + ACTION_WAIT_TICKS + ".");
            }
            return;
        }

        if (!isNextPageArrowPresent(screen)) {
            noArrowWaitTicks++;

            if (noArrowWaitTicks == 1) {
                warning("Arrow disappeared before click. Waiting up to 5000ms for it to reappear...");
            }

            if (noArrowWaitTicks >= NO_ARROW_MAX_WAIT_TICKS) {
                endedAtPage = currentPage;
                info("Arrow did not reappear after 5000ms. Finished at page " + endedAtPage + ".");
                state = State.FINISHED;
            }
            return;
        }

        noArrowWaitTicks = 0;
        debug("Clicking next page arrow at slot " + NEXT_PAGE_SLOT + " on page " + currentPage + ".");
        previousPageFingerprint = buildPageFingerprint(screen);
        clickSlot(screen, NEXT_PAGE_SLOT);
        currentPage++;
        armorStandSlots.clear();
        armorStandIndex = 0;
        emptyWaitTicks = 0;
        noArrowWaitTicks = 0;
        pageUpdateWaitTicks = 0;
        tickCounter = 0;
        info("Arrow clicked. Waiting for page " + currentPage + " to load...");
        state = State.WAITING_FOR_PAGE_UPDATE;
    }

    private void handleWaitingForPageUpdate() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            warning("GUI closed while waiting for page update.");
            endedAtPage = Math.max(1, currentPage - 1);
            state = State.FINISHED;
            return;
        }

        String currentFingerprint = buildPageFingerprint(screen);
        boolean changed = !currentFingerprint.equals(previousPageFingerprint);

        if (changed) {
            info("Page " + currentPage + " content updated. Settling before scan...");
            armorStandSlots.clear();
            armorStandIndex = 0;
            emptyWaitTicks = 0;
            noArrowWaitTicks = 0;
            pageUpdateWaitTicks = 0;
            pageSettleTicks = 0;
            tickCounter = 0;
            state = State.SETTLING_AFTER_PAGE_UPDATE;
            return;
        }

        pageUpdateWaitTicks++;
        if (DEBUG_LOGS && pageUpdateWaitTicks % HEARTBEAT_TICKS == 0) {
            debug("Still waiting for page update after " + (pageUpdateWaitTicks * 50) + "ms on page " + currentPage + ".");
        }

        if (pageUpdateWaitTicks >= PAGE_UPDATE_MAX_WAIT_TICKS) {
            warning("Page update was not detected after 5000ms. Settling and proceeding anyway.");
            armorStandSlots.clear();
            armorStandIndex = 0;
            emptyWaitTicks = 0;
            noArrowWaitTicks = 0;
            pageUpdateWaitTicks = 0;
            pageSettleTicks = 0;
            tickCounter = 0;
            state = State.SETTLING_AFTER_PAGE_UPDATE;
        }
    }

    private void handleSettlingAfterPageUpdate() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            warning("GUI closed while settling after page update.");
            endedAtPage = Math.max(1, currentPage - 1);
            state = State.FINISHED;
            return;
        }

        pageSettleTicks++;
        if (DEBUG_LOGS && (pageSettleTicks == 1 || pageSettleTicks == PAGE_SETTLE_TICKS)) {
            debug("Settling page " + currentPage + " before first armor-stand click: " + pageSettleTicks + "/" + PAGE_SETTLE_TICKS);
        }

        if (pageSettleTicks >= PAGE_SETTLE_TICKS) {
            pageSettleTicks = 0;
            tickCounter = 0;
            info("Now scanning page " + currentPage + "...");
            state = State.SCANNING_PAGE;
        }
    }

    private void discoverArmorStandSlots(GenericContainerScreen screen) {
        armorStandSlots.clear();

        for (int slotIndex = 0; slotIndex < screen.getScreenHandler().slots.size(); slotIndex++) {
            ItemStack stack = screen.getScreenHandler().getSlot(slotIndex).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.ARMOR_STAND) {
                armorStandSlots.add(slotIndex);
            }
        }

        if (DEBUG_LOGS) {
            debug("discoverArmorStandSlots: found=" + armorStandSlots.size() + " on page " + currentPage
                + " | screenTitle=" + sanitize(screen.getTitle().getString()));
        }
    }

    private int findFirstArmorStandSlot(GenericContainerScreen screen) {
        for (int slotIndex = 0; slotIndex < screen.getScreenHandler().slots.size(); slotIndex++) {
            ItemStack stack = screen.getScreenHandler().getSlot(slotIndex).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.ARMOR_STAND) return slotIndex;
        }
        return -1;
    }

    private String buildPageFingerprint(GenericContainerScreen screen) {
        StringBuilder fingerprint = new StringBuilder();
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Items.ARMOR_STAND || i == NEXT_PAGE_SLOT) {
                fingerprint
                    .append(i)
                    .append('=')
                    .append(Registries.ITEM.getId(stack.getItem()))
                    .append(':')
                    .append(sanitize(stack.getName().getString()))
                    .append(';');
            }
        }
        return fingerprint.toString();
    }

    private void readAndLogArmorStand(GenericContainerScreen screen, int slotId) {
        ItemStack stack = screen.getScreenHandler().getSlot(slotId).getStack();
        if (stack.isEmpty() || stack.getItem() != Items.ARMOR_STAND) {
            debug("Slot " + slotId + " is no longer an armor stand. item=" + (stack.isEmpty() ? "empty" : Registries.ITEM.getId(stack.getItem())));
            return;
        }

        List<String> lines = extractItemLines(stack);
        String displayName = sanitize(stack.getName().getString());
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        String dedupeKey = currentPage + ":" + slotId + ":" + displayName + ":" + String.join("|", lines);
        if (seenEntries.add(dedupeKey)) {
            entries.add(new ClanEntry(currentPage, slotId, itemId, displayName, lines));
            info("[Page " + currentPage + ", Slot " + slotId + "] " + displayName);
            for (String line : lines) {
                info("  - " + line);
            }
        }
    }

    private List<String> extractItemLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();

        String title = sanitize(stack.getName().getString());
        if (!title.isEmpty()) lines.add(title);

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            lore.lines().forEach(text -> {
                String line = sanitize(text.getString());
                if (!line.isEmpty()) lines.add(line);
            });
        }

        return lines;
    }

    private boolean isNextPageArrowPresent(GenericContainerScreen screen) {
        if (NEXT_PAGE_SLOT >= screen.getScreenHandler().slots.size()) return false;
        ItemStack stack = screen.getScreenHandler().getSlot(NEXT_PAGE_SLOT).getStack();
        boolean present = !stack.isEmpty() && stack.getItem() == Items.ARROW;
        if (DEBUG_LOGS) {
            String item = stack.isEmpty() ? "empty" : Registries.ITEM.getId(stack.getItem()).toString();
            debug("Arrow check slot " + NEXT_PAGE_SLOT + " => present=" + present + " item=" + item);
        }
        return present;
    }

    private void clickSlot(GenericContainerScreen screen, int slotId) {
        if (mc.interactionManager == null || mc.player == null) {
            debug("Cannot click slot " + slotId + " because interactionManager or player is null.");
            return;
        }

        ItemStack stack = screen.getScreenHandler().getSlot(slotId).getStack();
        String item = stack.isEmpty() ? "empty" : Registries.ITEM.getId(stack.getItem()).toString();
        debug("clickSlot syncId=" + screen.getScreenHandler().syncId + " slot=" + slotId + " item=" + item);

        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId,
            slotId,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private void finishAndReset() {
        if (endedAtPage == 0) endedAtPage = currentPage;
        saveProgressJson(endedAtPage);

        info("GetAllClans finished.");
        info("Entries logged: " + entries.size());
        info("Ended at page: " + endedAtPage);
        if (outputPath != null) info("Saved JSON: " + outputPath);

        if (mc.currentScreen != null && mc.player != null) {
            mc.player.closeHandledScreen();
        }

        state = State.IDLE;
        tickCounter = 0;
        currentPage = 0;
        initialClickWaitTicks = 0;
        emptyWaitTicks = 0;
        noArrowWaitTicks = 0;
        pageUpdateWaitTicks = 0;
        pageSettleTicks = 0;
        endedAtPage = 0;
        armorStandSlots.clear();
        armorStandIndex = 0;
        seenEntries.clear();
        entries.clear();
        startedAtIso = null;
        outputPath = null;
        outputFile = null;
        previousPageFingerprint = "";

        MeteorClient.EVENT_BUS.unsubscribe(this);
    }

    private void initializeOutputFile() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());
        outputFile = AddonTemplate.GetConfigFile("GetAllClans", "get_all_clans_" + timestamp + ".json");
        outputPath = outputFile.getAbsolutePath();
    }

    private void saveProgressJson(int pageValue) {
        if (outputFile == null) initializeOutputFile();

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Failed to create folder for JSON output: " + parent.getAbsolutePath());
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("command", "getallclans");
        root.addProperty("startedAt", startedAtIso);
        root.addProperty("finishedAt", Instant.now().toString());
        root.addProperty("endedAtPage", pageValue);
        root.addProperty("totalEntries", entries.size());

        JsonArray list = new JsonArray();
        for (ClanEntry entry : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("page", entry.page);
            obj.addProperty("slot", entry.slot);
            obj.addProperty("item", entry.item);
            obj.addProperty("displayName", entry.displayName);

            JsonArray lines = new JsonArray();
            for (String line : entry.lines) lines.add(line);
            obj.add("lines", lines);

            list.add(obj);
        }
        root.add("entries", list);

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            error("Failed to write JSON log: " + e.getMessage());
        }
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-or]", "").trim();
    }

    private void info(String msg) {
        ChatUtils.info("GetAllClans: " + msg);
    }

    private void warning(String msg) {
        ChatUtils.warning("GetAllClans: " + msg);
    }

    private void error(String msg) {
        ChatUtils.error("GetAllClans: " + msg);
    }

    private void debug(String msg) {
        if (!DEBUG_LOGS) return;
        ChatUtils.info("GetAllClans [debug]: " + msg);
    }

    private String getScreenName() {
        if (mc.currentScreen == null) return "null";
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            return "GenericContainerScreen(" + sanitize(screen.getTitle().getString()) + ")";
        }
        return mc.currentScreen.getClass().getSimpleName();
    }

    private static class ClanEntry {
        final int page;
        final int slot;
        final String item;
        final String displayName;
        final List<String> lines;

        ClanEntry(int page, int slot, String item, String displayName, List<String> lines) {
            this.page = page;
            this.slot = slot;
            this.item = item;
            this.displayName = displayName;
            this.lines = lines;
        }
    }
}