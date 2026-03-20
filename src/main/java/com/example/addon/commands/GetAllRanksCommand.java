package com.example.addon.commands;

import com.example.addon.AddonTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetAllRanksCommand extends Command {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern RANK_PATTERN = Pattern.compile("(?i)\\b(Hercules|Titan|Ares|Zeus)\\b\\s+([A-Za-z0-9_]{3,16})");

    public GetAllRanksCommand() {
        super("getallranks", "Gets all ranked player names from the tablist and writes them to JSON.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            run();
            return SINGLE_SUCCESS;
        });
    }

    private void run() {
        if (mc.getNetworkHandler() == null) {
            error("Network handler is not available. Join a server first.");
            return;
        }

        String startedAtIso = Instant.now().toString();
        List<RankEntry> entries = new ArrayList<>();
        Map<String, RankEntry> dedupe = new HashMap<>();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String display = getDisplayName(entry);
            String sanitized = sanitize(display);
            Matcher matcher = RANK_PATTERN.matcher(sanitized);
            if (!matcher.find()) continue;

            String rank = normalizeRank(matcher.group(1));
            String player = matcher.group(2);
            String key = player.toLowerCase(Locale.ROOT);

            RankEntry rankEntry = new RankEntry(player, rank, sanitized);
            dedupe.put(key, rankEntry);
        }

        entries.addAll(dedupe.values());
        entries.sort((a, b) -> a.playerName.compareToIgnoreCase(b.playerName));

        info("GetAllRanks finished. Found " + entries.size() + " ranked players.");
        if (!entries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (RankEntry entry : entries) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.rank).append(" ").append(entry.playerName);
            }
            info("Ranks: " + sb);
        }

        File outputFile = getOutputFile();
        String outputPath = outputFile.getAbsolutePath();
        saveJson(outputFile, startedAtIso, entries);
        info("Saved JSON: " + outputPath);
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

    private void saveJson(File outputFile, String startedAtIso, List<RankEntry> entries) {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Failed to create folder for JSON output: " + parent.getAbsolutePath());
            return;
        }

        Map<String, RankEntry> merged = new HashMap<>();
        String existingStartedAt = loadExistingEntries(outputFile, merged);
        for (RankEntry entry : entries) {
            merged.put(entry.playerName.toLowerCase(Locale.ROOT), entry);
        }

        List<RankEntry> finalEntries = new ArrayList<>(merged.values());
        finalEntries.sort((a, b) -> a.playerName.compareToIgnoreCase(b.playerName));

        JsonObject root = new JsonObject();
        root.addProperty("command", "getallranks");
        root.addProperty("startedAt", existingStartedAt != null ? existingStartedAt : startedAtIso);
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
        return text.replaceAll("Â§[0-9a-fk-or]", "").trim();
    }

    private void info(String msg) {
        ChatUtils.info("GetAllRanks: " + msg);
    }

    private void error(String msg) {
        ChatUtils.error("GetAllRanks: " + msg);
    }

    private String loadExistingEntries(File outputFile, Map<String, RankEntry> merged) {
        if (!outputFile.exists()) return null;

        try (FileReader reader = new FileReader(outputFile)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) return null;
            JsonObject root = element.getAsJsonObject();

            String existingStartedAt = null;
            if (root.has("startedAt") && root.get("startedAt").isJsonPrimitive()) {
                existingStartedAt = root.get("startedAt").getAsString();
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

            return existingStartedAt;
        } catch (IOException e) {
            error("Failed to read existing JSON: " + e.getMessage());
            return null;
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
