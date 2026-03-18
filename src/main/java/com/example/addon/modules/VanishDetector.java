package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VanishDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> targets = sgGeneral.add(new StringListSetting.Builder()
        .name("targets")
        .description("Players to track. Empty means all suggestions.")
        .defaultValue(List.of(
            "GabiChan",
            "papaseca",
            "ItzPionix",
            "AnubisKing1",
            "NaotmoST",
            "NotAem",
            "zViroxx",
            "Fedes10",
            "devdani",
            "zSarex",
            "zAnnu32",
            "CO1N_",
            "ImLeXx15",
            "iCicada",
            "Ryu_Xyz",
            "SaimeonHwy",
            "Hyper_Plus",
            "mesly",
            "MindlessEwee",
            "SrMonkey57",
            "RyoKeev",
            "JuarezXR",
            "SoyDxvid",
            "AlonePart3",
            "Codificada",
            "apaloe_",
            "Aritzel",
            "SotoAsa",
            "Beykih",
            "zMazon",
            "GiannyGamerPro",
            "erneto13",
            "MitKema",
            "R0nalito",
            "Jakzi",
            "Andregar37",
            "drastiko",
            "MrJadyel",
            "Edusthetic",
            "Hosve"
        ))
        .build()
    );

    private final Setting<Integer> checkIntervalSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("check-interval-seconds")
        .description("How often to request /msg suggestions.")
        .defaultValue(2)
        .min(2)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> perTargetRequests = sgGeneral.add(new BoolSetting.Builder()
        .name("per-target-requests")
        .description("Request /msg suggestions per target (more reliable on some servers).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> perTargetRequestsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("per-target-requests-per-tick")
        .description("How many targets to request each interval when per-target mode is on.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .visible(perTargetRequests::get)
        .build()
    );

    private final Setting<Boolean> notifyWhenVisible = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-when-visible")
        .description("Notify when a tracked player is no longer vanished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyVisibleOnline = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-visible-online")
        .description("Notify when a tracked player is visible in the tablist.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hotAdminsEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("hot-admins")
        .description("Track admins that recently banned someone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotAdminMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("hot-admins-minutes")
        .description("How long an admin stays hot after a ban.")
        .defaultValue(30)
        .min(1)
        .sliderMax(120)
        .visible(hotAdminsEnabled::get)
        .build()
    );

    private final Setting<Boolean> notifyHotAdmins = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-hot-admins")
        .description("Notify when an admin becomes hot.")
        .defaultValue(true)
        .visible(hotAdminsEnabled::get)
        .build()
    );

    private final Setting<Boolean> joinLeaveAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("join-leave-alerts")
        .description("Alert when tracked players join/leave too often.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> joinLeaveThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("join-leave-threshold")
        .description("Number of joins/leaves within the window to trigger an alert.")
        .defaultValue(3)
        .min(2)
        .sliderMax(10)
        .visible(joinLeaveAlerts::get)
        .build()
    );

    private final Setting<Integer> joinLeaveWindowSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("join-leave-window-seconds")
        .description("Time window for joins/leaves counting.")
        .defaultValue(60)
        .min(10)
        .sliderMax(300)
        .visible(joinLeaveAlerts::get)
        .build()
    );

    private final Setting<Integer> joinLeaveCooldownSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("join-leave-cooldown-seconds")
        .description("Minimum time between alerts per player.")
        .defaultValue(60)
        .min(5)
        .sliderMax(300)
        .visible(joinLeaveAlerts::get)
        .build()
    );

    private final Setting<Boolean> recentAdminsEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("recent-admins")
        .description("Track recently seen admins after they leave.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> recentAdminsMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("recent-admins-minutes")
        .description("How long an admin stays in recent after leaving.")
        .defaultValue(30)
        .min(1)
        .sliderMax(120)
        .visible(recentAdminsEnabled::get)
        .build()
    );

    private int lastRequestId = 0;
    private long lastRequestTimeMs = 0;
    private final Set<String> lastVanished = new HashSet<>();
    private final Set<String> lastVisible = new HashSet<>();
    private final Map<String, String> lastVisibleDisplay = new HashMap<>();
    private final List<String> currentVisibleDisplay = new ArrayList<>();
    private final List<String> currentVanishedDisplay = new ArrayList<>();
    private final Set<String> currentVanishedKeys = new HashSet<>();
    private final Map<String, Long> suggestedTargetsSeen = new HashMap<>();
    private int targetRequestIndex = 0;
    private String lastRequestedTargetKey;
    private String lastRequestedAutocompletePrefix;
    private final Map<String, Long> hotAdmins = new HashMap<>();
    private final Map<String, String> hotAdminsDisplay = new HashMap<>();
    private final Map<String, Integer> hotAdminBanCounts = new HashMap<>();
    private final List<String> currentHotDisplay = new ArrayList<>();
    private final Map<String, Long> recentAdmins = new HashMap<>();
    private final Map<String, String> recentAdminsDisplay = new HashMap<>();
    private final List<String> currentRecentDisplay = new ArrayList<>();
    private final Map<String, ArrayDeque<Long>> joinLeaveEvents = new HashMap<>();
    private final Map<String, Long> lastJoinLeaveAlert = new HashMap<>();

    private static final Pattern STAFF_PATTERN = Pattern.compile("Staff:\\s*([^/]+)", Pattern.CASE_INSENSITIVE);

    public VanishDetector() {
        super(AddonTemplate.CATEGORY, "vanish-detector", "Detects vanished players via /msg suggestions.");
    }

    @Override
    public void onActivate() {
        lastRequestTimeMs = 0;
        lastVanished.clear();
        lastVisible.clear();
        joinLeaveEvents.clear();
        lastJoinLeaveAlert.clear();
        currentVisibleDisplay.clear();
        currentVanishedDisplay.clear();
        currentVanishedKeys.clear();
        suggestedTargetsSeen.clear();
        targetRequestIndex = 0;
        lastRequestedTargetKey = null;
        lastVisibleDisplay.clear();

        cleanupHotAdmins();
        cleanupRecentAdmins();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        cleanupHotAdmins();
        cleanupRecentAdmins();
        updateVisibleFromTab();

        long now = System.currentTimeMillis();
        long intervalMs = checkIntervalSeconds.get() * 1000L;
        if (now - lastRequestTimeMs < intervalMs) return;

        lastRequestTimeMs = now;
        if (perTargetRequests.get() && !targets.get().isEmpty()) {
            int count = perTargetRequestsPerTick.get();
            for (int i = 0; i < count; i++) {
                lastRequestId++;
                String target = targets.get().get(targetRequestIndex % targets.get().size());
                targetRequestIndex++;
                lastRequestedTargetKey = normalize(target);
                lastRequestedAutocompletePrefix = getAutocompletePrefix(target);
                mc.getNetworkHandler().sendPacket(new RequestCommandCompletionsC2SPacket(lastRequestId, "/msg " + lastRequestedAutocompletePrefix));
            }
        } else {
            lastRequestId++;
            lastRequestedTargetKey = null;
            lastRequestedAutocompletePrefix = null;
            mc.getNetworkHandler().sendPacket(new RequestCommandCompletionsC2SPacket(lastRequestId, "/msg "));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof CommandSuggestionsS2CPacket packet) {
            handleSuggestions(packet.getSuggestions());
        } else if (event.packet instanceof PlayerListS2CPacket packet) {
            if (joinLeaveAlerts.get() && packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
                    if (entry.profile() == null) continue;
                    recordJoinLeave(entry.profile().getName());
                }
            }
        } else if (event.packet instanceof PlayerRemoveS2CPacket packet) {
            if (!joinLeaveAlerts.get() || mc.getNetworkHandler() == null) return;
            for (var id : packet.profileIds()) {
                PlayerListEntry toRemove = mc.getNetworkHandler().getPlayerListEntry(id);
                if (toRemove == null) continue;
                recordJoinLeave(toRemove.getProfile().getName());
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!hotAdminsEnabled.get()) return;
        String text = event.getMessage().getString();
        Matcher matcher = STAFF_PATTERN.matcher(text);
        if (!matcher.find()) return;
        String staffName = matcher.group(1).trim();
        if (!staffName.isEmpty()) recordHotAdmin(staffName);
    }

    private void handleSuggestions(Suggestions suggestions) {
        if (mc.getNetworkHandler() == null) return;

        if (perTargetRequests.get() && !targets.get().isEmpty()) {
            handlePerTargetSuggestions(suggestions);
            return;
        }

        Map<String, String> suggestionNames = new HashMap<>();
        for (Suggestion suggestion : suggestions.getList()) {
            String name = suggestion.getText();
            suggestionNames.put(normalize(name), name);
        }

        Set<String> vanished = new HashSet<>(suggestionNames.keySet());
        vanished.removeAll(lastVisible);
        applyTargetFilter(vanished);

        updateVanishedDisplay(vanished, suggestionNames);

        if (!vanished.equals(lastVanished)) {
            Set<String> added = new HashSet<>(vanished);
            added.removeAll(lastVanished);
            Set<String> removed = new HashSet<>(lastVanished);
            removed.removeAll(vanished);

            if (!added.isEmpty()) {
                ChatUtils.info("Vanish: " + formatNames(added, suggestionNames));
            }
            if (notifyWhenVisible.get() && !removed.isEmpty()) {
                ChatUtils.info("Visible: " + formatNames(removed, suggestionNames));
            }

            lastVanished.clear();
            lastVanished.addAll(vanished);
        }
    }

    private void handlePerTargetSuggestions(Suggestions suggestions) {
        long now = System.currentTimeMillis();
        if (lastRequestedTargetKey != null && lastRequestedAutocompletePrefix != null) {
            boolean found = false;
            for (Suggestion suggestion : suggestions.getList()) {
                String suggestionText = suggestion.getText();
                // The suggestion should be the target name (which starts with our autocomplete prefix)
                if (normalize(suggestionText).equals(lastRequestedTargetKey) ||
                    normalize(suggestionText).startsWith(lastRequestedAutocompletePrefix.toLowerCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            if (found) suggestedTargetsSeen.put(lastRequestedTargetKey, now);
        }

        Map<String, String> targetDisplay = new HashMap<>();
        for (String target : targets.get()) targetDisplay.put(normalize(target), target);

        long ttlMs = checkIntervalSeconds.get() * 1000L * Math.max(1, targets.get().size()) * 2L;
        Set<String> suggestedKeys = new HashSet<>();
        for (var entry : suggestedTargetsSeen.entrySet()) {
            if (now - entry.getValue() <= ttlMs) suggestedKeys.add(entry.getKey());
        }

        Set<String> vanished = new HashSet<>(suggestedKeys);
        vanished.removeAll(lastVisible);
        applyTargetFilter(vanished);

        updateVanishedDisplay(vanished, targetDisplay);

        if (!vanished.equals(lastVanished)) {
            Set<String> added = new HashSet<>(vanished);
            added.removeAll(lastVanished);
            Set<String> removed = new HashSet<>(lastVanished);
            removed.removeAll(vanished);

            if (!added.isEmpty()) {
                ChatUtils.info("Vanish: " + formatNames(added, targetDisplay));
            }
            if (notifyWhenVisible.get() && !removed.isEmpty()) {
                ChatUtils.info("Visible: " + formatNames(removed, targetDisplay));
            }

            lastVanished.clear();
            lastVanished.addAll(vanished);
        }
    }

    private void recordJoinLeave(String name) {
        if (!isTarget(name)) return;

        long now = System.currentTimeMillis();
        long windowMs = joinLeaveWindowSeconds.get() * 1000L;
        long cooldownMs = joinLeaveCooldownSeconds.get() * 1000L;

        String key = normalize(name);
        ArrayDeque<Long> times = joinLeaveEvents.computeIfAbsent(key, k -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.removeFirst();

        if (times.size() >= joinLeaveThreshold.get()) {
            long lastAlert = lastJoinLeaveAlert.getOrDefault(key, 0L);
            if (now - lastAlert >= cooldownMs) {
                lastJoinLeaveAlert.put(key, now);
                ChatUtils.info("Join/Leave: " + name + " (" + times.size() + " in " + joinLeaveWindowSeconds.get() + "s)");
            }
        }
    }

    private boolean isTarget(String name) {
        if (targets.get().isEmpty()) return true;
        String key = normalize(name);
        for (String target : targets.get()) {
            if (normalize(target).equals(key)) return true;
        }
        return false;
    }

    private void updateDisplayLists(Set<String> currentVisible, Map<String, String> tabDisplay, Set<String> vanished, Map<String, String> suggestionNames) {
        currentVisibleDisplay.clear();
        currentVisibleDisplay.addAll(buildDisplayList(currentVisible, tabDisplay));

        currentVanishedDisplay.clear();
        currentVanishedDisplay.addAll(buildDisplayList(vanished, suggestionNames));
        currentVanishedKeys.clear();
        currentVanishedKeys.addAll(vanished);
    }

    private void updateVanishedDisplay(Set<String> vanished, Map<String, String> suggestionNames) {
        currentVanishedDisplay.clear();
        currentVanishedDisplay.addAll(buildDisplayList(vanished, suggestionNames));
        currentVanishedKeys.clear();
        currentVanishedKeys.addAll(vanished);
    }

    private void updateVisibleFromTab() {
        if (mc.getNetworkHandler() == null) return;
        Set<String> tabNames = new HashSet<>();
        Map<String, String> tabDisplay = new HashMap<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            String key = normalize(name);
            tabNames.add(key);
            tabDisplay.put(key, name);
        }

        Set<String> currentVisible = new HashSet<>(tabNames);
        applyTargetFilter(currentVisible);

        currentVisibleDisplay.clear();
        currentVisibleDisplay.addAll(buildDisplayList(currentVisible, tabDisplay));
        lastVisibleDisplay.clear();
        lastVisibleDisplay.putAll(tabDisplay);

        if (notifyVisibleOnline.get()) {
            Set<String> visibleAdded = new HashSet<>(currentVisible);
            visibleAdded.removeAll(lastVisible);
            visibleAdded.removeAll(lastVanished);

            Set<String> visibleRemoved = new HashSet<>(lastVisible);
            visibleRemoved.removeAll(currentVisible);
            visibleRemoved.removeAll(lastVanished);

            if (!visibleAdded.isEmpty()) {
                ChatUtils.info("Online: " + formatNames(visibleAdded, tabDisplay));
            }
            if (!visibleRemoved.isEmpty()) {
                ChatUtils.info("Offline: " + formatNames(visibleRemoved, tabDisplay));
            }
        }

        if (recentAdminsEnabled.get()) {
            boolean recentChanged = false;
            var recentIt = recentAdmins.keySet().iterator();
            while (recentIt.hasNext()) {
                String key = recentIt.next();
                if (currentVisible.contains(key) || currentVanishedKeys.contains(key)) {
                    recentIt.remove();
                    recentAdminsDisplay.remove(key);
                    recentChanged = true;
                }
            }
            if (recentChanged) rebuildRecentDisplay();

            Set<String> justLeft = new HashSet<>(lastVisible);
            justLeft.removeAll(currentVisible);
            justLeft.removeAll(lastVanished);
            justLeft.removeAll(currentVanishedKeys);
            for (String nameKey : justLeft) {
                String display = lastVisibleDisplay.getOrDefault(nameKey, nameKey);
                recordRecentAdmin(display);
            }
        }

        lastVisible.clear();
        lastVisible.addAll(currentVisible);
    }

    private static List<String> buildDisplayList(Set<String> names, Map<String, String> lookup) {
        List<String> display = new ArrayList<>();
        for (String key : names) display.add(lookup.getOrDefault(key, key));
        display.sort(String.CASE_INSENSITIVE_ORDER);
        return display;
    }

    private void applyTargetFilter(Set<String> names) {
        if (targets.get().isEmpty()) return;
        Set<String> targetKeys = new HashSet<>();
        for (String target : targets.get()) targetKeys.add(normalize(target));
        names.retainAll(targetKeys);
    }

    public List<String> getVisibleDisplayNames() {
        return new ArrayList<>(currentVisibleDisplay);
    }

    public List<String> getVanishedDisplayNames() {
        return new ArrayList<>(currentVanishedDisplay);
    }

    public List<String> getHotAdminsDisplayNames() {
        cleanupHotAdmins();
        return new ArrayList<>(currentHotDisplay);
    }

    public List<String> getRecentAdminsDisplayNames() {
        cleanupRecentAdmins();
        return new ArrayList<>(currentRecentDisplay);
    }

    private void recordHotAdmin(String name) {
        if (!isTarget(name)) return;
        long now = System.currentTimeMillis();
        long until = now + (hotAdminMinutes.get() * 60_000L);
        String key = normalize(name);
        boolean wasHot = hotAdmins.containsKey(key) && hotAdmins.get(key) > now;

        hotAdmins.put(key, until);
        hotAdminsDisplay.put(key, name);
        hotAdminBanCounts.put(key, hotAdminBanCounts.getOrDefault(key, 0) + 1);
        rebuildHotDisplay();

        if (notifyHotAdmins.get() && !wasHot) {
            ChatUtils.info("Hot ban: " + name + " (" + hotAdminMinutes.get() + "m)");
        }
    }

    private void cleanupHotAdmins() {
        if (hotAdmins.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        var it = hotAdmins.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                hotAdminsDisplay.remove(entry.getKey());
                hotAdminBanCounts.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) rebuildHotDisplay();
    }

    private void rebuildHotDisplay() {
        currentHotDisplay.clear();
        List<String> display = new ArrayList<>();
        for (String key : hotAdminsDisplay.keySet()) {
            String name = hotAdminsDisplay.getOrDefault(key, key);
            int count = hotAdminBanCounts.getOrDefault(key, 1);
            display.add(name + " (x" + count + ")");
        }
        display.sort(String.CASE_INSENSITIVE_ORDER);
        currentHotDisplay.addAll(display);
    }

    private void recordRecentAdmin(String name) {
        if (!recentAdminsEnabled.get() || !isTarget(name)) return;
        long now = System.currentTimeMillis();
        long until = now + (recentAdminsMinutes.get() * 60_000L);
        String key = normalize(name);
        recentAdmins.put(key, until);
        recentAdminsDisplay.put(key, name);
        rebuildRecentDisplay();
    }

    private void cleanupRecentAdmins() {
        if (recentAdmins.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        var it = recentAdmins.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                recentAdminsDisplay.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) rebuildRecentDisplay();
    }

    private void rebuildRecentDisplay() {
        currentRecentDisplay.clear();
        currentRecentDisplay.addAll(buildDisplayList(recentAdminsDisplay.keySet(), recentAdminsDisplay));
    }

    private static String formatNames(Set<String> names, Map<String, String> lookup) {
        List<String> display = new ArrayList<>();
        for (String key : names) display.add(lookup.getOrDefault(key, key));
        display.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", display);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the autocomplete prefix for a target name.
     * This is the full name except the last letter.
     * For "Notch", returns "Notc" so that "/msg Notc" autocompletes to "Notch".
     */
    private static String getAutocompletePrefix(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.substring(0, name.length() - 1);
    }
}
