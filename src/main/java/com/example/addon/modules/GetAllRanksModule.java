package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetAllRanksModule extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern RANK_PATTERN = Pattern.compile("(?i)\\b(Hercules|Titan|Ares|Zeus)\\b\\s+([A-Za-z0-9_]{3,16})");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scanIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval-ticks")
        .description("How often to rescan the tablist.")
        .defaultValue(1)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> logNewEntries = sgGeneral.add(new BoolSetting.Builder()
        .name("log-new-entries")
        .description("Log when new ranked players are found.")
        .defaultValue(true)
        .build()
    );

    private final Map<String, RankEntry> dedupe = new HashMap<>();
    private String startedAtIso = null;
    private int ticksSinceScan = 0;

    public GetAllRanksModule() {
        super(AddonTemplate.CATEGORY, "getallranks", "Continuously scans the tablist and records ranked players.");
    }

    @Override
    public void onActivate() {
        ticksSinceScan = 0;
        dedupe.clear();
        startedAtIso = Instant.now().toString();
        loadExistingEntries(getOutputFile(), dedupe);
    }

    @Override
    public void onDeactivate() {
        saveJson(getOutputFile());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.getNetworkHandler() == null) return;

        ticksSinceScan++;
        if (ticksSinceScan < scanIntervalTicks.get()) return;
        ticksSinceScan = 0;

        int newEntries = 0;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String display = getDisplayName(entry);
            String sanitized = sanitize(display);
            Matcher matcher = RANK_PATTERN.matcher(sanitized);
            if (!matcher.find()) continue;

            String rank = normalizeRank(matcher.group(1));
            String player = matcher.group(2);
            String key = player.toLowerCase(Locale.ROOT);

            if (!dedupe.containsKey(key)) {
                dedupe.put(key, new RankEntry(player, rank, sanitized));
                newEntries++;
            }
        }

        if (newEntries > 0) {
            saveJson(getOutputFile());
            if (logNewEntries.get()) {
                info("Added " + newEntries + " new ranked players. Total: " + dedupe.size());
            }
        }
    }

    private String getDisplayName(PlayerListEntry entry) {
        Text displayName = entry.getDisplayName();
        if (displayName != null) return displayName.getString();
        if (entry.getProfile() != null) return entry.getProfile().getName();
        return "";
    }

    private File getOutputFile() {
        return AddonTemplate.GetConfigFile("GetAllRanks", "get_all_ranks.json");
    }

    private void saveJson(File outputFile) {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Failed to create folder for JSON output: " + parent.getAbsolutePath());
            return;
        }

        List<RankEntry> finalEntries = new ArrayList<>(dedupe.values());
        finalEntries.sort((a, b) -> a.playerName.compareToIgnoreCase(b.playerName));

        JsonObject root = new JsonObject();
        root.addProperty("command", "getallranks");
        root.addProperty("startedAt", startedAtIso);
        root.addProperty("finishedAt", Instant.now().toString());
        root.addProperty("totalEntries", finalEntries.size());

        JsonArray list = new JsonArray();
        for (RankEntry entry : finalEntries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("player", entry.playerName);
            obj.addProperty("rank", entry.rank);
            obj.addProperty("displayName", entry.displayName);
            list.add(obj);
        }
        root.add("entries", list);

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            error("Failed to write JSON log: " + e.getMessage());
        }
    }

    private String normalizeRank(String rank) {
        String lower = rank.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "hercules" -> "Hercules";
            case "titan" -> "Titan";
            case "ares" -> "Ares";
            case "zeus" -> "Zeus";
            default -> rank;
        };
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("Ã‚Â§[0-9a-fk-or]", "").trim();
    }

    private void info(String msg) {
        ChatUtils.info("GetAllRanks: " + msg);
    }

    private void error(String msg) {
        ChatUtils.error("GetAllRanks: " + msg);
    }

    private void loadExistingEntries(File outputFile, Map<String, RankEntry> merged) {
        if (!outputFile.exists()) return;

        try (FileReader reader = new FileReader(outputFile)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();

            if (root.has("startedAt") && root.get("startedAt").isJsonPrimitive()) {
                startedAtIso = root.get("startedAt").getAsString();
            }

            if (root.has("entries") && root.get("entries").isJsonArray()) {
                JsonArray list = root.getAsJsonArray("entries");
                for (JsonElement entryElement : list) {
                    if (!entryElement.isJsonObject()) continue;
                    JsonObject obj = entryElement.getAsJsonObject();
                    if (!obj.has("player") || !obj.has("rank")) continue;
                    String player = obj.get("player").getAsString();
                    String rank = obj.get("rank").getAsString();
                    String display = obj.has("displayName") ? obj.get("displayName").getAsString() : (rank + " " + player);
                    merged.put(player.toLowerCase(Locale.ROOT), new RankEntry(player, rank, display));
                }
            }
        } catch (IOException e) {
            error("Failed to read existing JSON: " + e.getMessage());
        }
    }

    private static class RankEntry {
        final String playerName;
        final String rank;
        final String displayName;

        RankEntry(String playerName, String rank, String displayName) {
            this.playerName = playerName;
            this.rank = rank;
            this.displayName = displayName;
        }
    }
}
