package com.example.addon.commands;

import com.example.addon.utils.ChatPacketSender;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command to parse clan members from /clan info GUI.
 * Usage: .clanMembers <clanName>
 */
public class ClanMembersCommand extends Command {

    // State machine states
    private enum State {
        IDLE,
        WAITING_FOR_GUI,
        WAITING_FOR_SIGN_CLICK,
        PARSING_MEMBERS,
        CLICKING_NEXT_PAGE,
        FINISHED
    }

    private State currentState = State.IDLE;
    private String targetClanName = null;
    private final Set<String> collectedMembers = new HashSet<>();
    private int tickCounter = 0;
    private int pageCount = 0;
    private static final int SIGN_SLOT = 19;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int WAIT_TICKS = 10; // Wait 0.5 seconds between actions

    public ClanMembersCommand() {
        super("clan-members", "Parses clan members from /clan info GUI.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("clanName", StringArgumentType.greedyString()).executes(context -> {
            String clanName = StringArgumentType.getString(context, "clanName");
            startClanParsing(clanName);
            return SINGLE_SUCCESS;
        }));
    }

    private void startClanParsing(String clanName) {
        if (currentState != State.IDLE) {
            error("Already parsing a clan! Wait for it to finish or restart the game.");
            return;
        }

        targetClanName = clanName;
        collectedMembers.clear();
        tickCounter = 0;
        pageCount = 0;
        currentState = State.WAITING_FOR_GUI;

        info("Starting clan member parsing for: " + clanName);
        info("Step 1/5: Executing /clan info " + clanName);

        // Subscribe to events
        MeteorClient.EVENT_BUS.subscribe(this);

        // Execute the command
        ChatPacketSender.sendCommand("clan info " + clanName);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (currentState == State.IDLE) return;

        tickCounter++;

        switch (currentState) {
            case WAITING_FOR_GUI:
                handleWaitingForGui();
                break;
            case WAITING_FOR_SIGN_CLICK:
                handleWaitingForSignClick();
                break;
            case PARSING_MEMBERS:
                handleParsingMembers();
                break;
            case CLICKING_NEXT_PAGE:
                handleClickingNextPage();
                break;
            case FINISHED:
                handleFinished();
                break;
        }
    }

    private void handleWaitingForGui() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            info("Step 2/5: GUI opened! Title: " + screen.getTitle().getString());
            info("Waiting briefly before clicking sign at slot " + SIGN_SLOT + "...");
            tickCounter = 0;
            currentState = State.WAITING_FOR_SIGN_CLICK;
        } else if (tickCounter > 100) { // 5 seconds timeout
            error("Timeout: GUI did not open within 5 seconds.");
            resetState();
        }
    }

    private void handleWaitingForSignClick() {
        if (tickCounter < WAIT_TICKS) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            error("GUI was closed unexpectedly!");
            resetState();
            return;
        }

        // Check if slot 19 has an OAK_SIGN
        ItemStack signItem = screen.getScreenHandler().getSlot(SIGN_SLOT).getStack();
        if (signItem.getItem() != Items.OAK_SIGN) {
            warning("Slot " + SIGN_SLOT + " does not contain an OAK_SIGN! Found: " + signItem.getItem());
        }

        info("Clicking sign at slot " + SIGN_SLOT + "...");
        clickSlot(screen, SIGN_SLOT);

        tickCounter = 0;
        currentState = State.PARSING_MEMBERS;
    }

    private void handleParsingMembers() {
        if (tickCounter < WAIT_TICKS) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            error("GUI was closed unexpectedly during parsing!");
            resetState();
            return;
        }

        pageCount++;
        info("Parsing page " + pageCount + " for member heads...");

        int membersOnPage = 0;
        // Iterate through all slots to find player heads
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            ItemStack stack = slot.getStack();

            // Check if it's a player head
            if (stack.getItem() == Items.PLAYER_HEAD) {
                String playerName = extractPlayerName(stack);
                if (playerName != null && !playerName.isEmpty()) {
                    if (collectedMembers.add(playerName)) {
                        membersOnPage++;
                    }
                }
            }
        }

        info("Found " + membersOnPage + " new members on page " + pageCount + 
             " (Total unique: " + collectedMembers.size() + ")");

        // Check for next page arrow
        ItemStack nextPageItem = screen.getScreenHandler().getSlot(NEXT_PAGE_SLOT).getStack();
        if (nextPageItem.getItem() == Items.ARROW) {
            info("Next page arrow detected at slot " + NEXT_PAGE_SLOT);
            tickCounter = 0;
            currentState = State.CLICKING_NEXT_PAGE;
        } else {
            info("No more pages (slot " + NEXT_PAGE_SLOT + " is not an arrow).");
            currentState = State.FINISHED;
        }
    }

    private void handleClickingNextPage() {
        if (tickCounter < WAIT_TICKS) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            error("GUI was closed unexpectedly!");
            resetState();
            return;
        }

        info("Clicking next page arrow at slot " + NEXT_PAGE_SLOT + "...");
        clickSlot(screen, NEXT_PAGE_SLOT);

        tickCounter = 0;
        currentState = State.PARSING_MEMBERS;
    }

    private void handleFinished() {
        info("Step 5/5: Finished! Closing GUI and displaying results...");

        // Close the GUI
        if (mc.currentScreen != null && mc.player != null) {
            mc.player.closeHandledScreen();
        }

        // Display results
        displayResults();

        resetState();
    }

    private String extractPlayerName(ItemStack headStack) {
        if (headStack.isEmpty()) return null;

        // Get the display name from the item
        String name = headStack.getName().getString();
        if (name != null && !name.isEmpty()) {
            // Remove formatting codes if any
            name = name.replaceAll("§[0-9a-fk-or]", "").trim();
            return name;
        }

        return null;
    }

    private void clickSlot(GenericContainerScreen screen, int slotId) {
        if (mc.interactionManager == null || mc.player == null) return;

        // Use the proper method to click a slot
        mc.interactionManager.clickSlot(
            screen.getScreenHandler().syncId,
            slotId,
            0, // Left click
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private void displayResults() {
        info("========================================");
        info("Clan Members for: " + targetClanName);
        info("Total Members Found: " + collectedMembers.size());
        info("Pages Scanned: " + pageCount);
        info("========================================");

        if (collectedMembers.isEmpty()) {
            warning("No members were found!");
        } else {
            // Sort alphabetically and display
            List<String> sortedMembers = new ArrayList<>(collectedMembers);
            sortedMembers.sort(String.CASE_INSENSITIVE_ORDER);

            StringBuilder sb = new StringBuilder();
            for (String member : sortedMembers) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(member);
            }

            info("Members: " + sb);

            // Also copy to clipboard
            if (mc.keyboard != null) {
                mc.keyboard.setClipboard(String.join(", ", sortedMembers));
                info("(Member list copied to clipboard!)");
            }
        }
    }

    private void resetState() {
        currentState = State.IDLE;
        targetClanName = null;
        tickCounter = 0;
        pageCount = 0;
        MeteorClient.EVENT_BUS.unsubscribe(this);
        info("Clan parsing process ended.");
    }

    // Helper methods for different log levels
    private void info(String message) {
        ChatUtils.info("ClanMembers: " + message);
    }

    private void warning(String message) {
        ChatUtils.warning("ClanMembers: " + message);
    }

    private void error(String message) {
        ChatUtils.error("ClanMembers: " + message);
    }
}
