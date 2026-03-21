package com.example.addon.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SyncManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SYNC_FILE = new File(FabricLoader.getInstance().getGameDir().toFile(), "sync.json");
    private static final File TMP_FILE = new File(FabricLoader.getInstance().getGameDir().toFile(), "sync.tmp.json");

    public static class SyncState {
        public boolean client1_ready = false;
        public boolean client2_ready = false;
        public long execute_timestamp = 0;
        public boolean completed = false;
    }

    public static synchronized SyncState readState() {
        if (!SYNC_FILE.exists()) {
            SyncState defaultState = new SyncState();
            writeState(defaultState);
            return defaultState;
        }
        for (int i = 0; i < 3; i++) {
            try (FileReader reader = new FileReader(SYNC_FILE)) {
                SyncState state = GSON.fromJson(reader, SyncState.class);
                return state != null ? state : new SyncState();
            } catch (IOException e) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
        return new SyncState();
    }

    public static synchronized void writeState(SyncState state) {
        for (int i = 0; i < 3; i++) {
            try (FileWriter writer = new FileWriter(TMP_FILE)) {
                GSON.toJson(state, writer);
                writer.flush();
                writer.close();
                Files.move(TMP_FILE.toPath(), SYNC_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                break;
            } catch (IOException e) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
    }
}
