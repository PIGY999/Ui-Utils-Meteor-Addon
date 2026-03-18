package com.example.addon.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Mixin that continuously dumps Vicore strings as modules are loaded.
 * Runs every 5 seconds to catch newly loaded classes.
 */
@Mixin(MinecraftClient.class)
public class VicoreStringDumper {
    
    private static boolean started = false;
    private static final String OUTPUT_PATH = System.getProperty("user.home") + "\\Desktop\\vicore_all_strings.txt";
    private static Set<String> capturedKeys = ConcurrentHashMap.newKeySet();
    private static int totalStrings = 0;
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (started) return;
        started = true;
        
        System.out.println("[VICORE-DUMPER] Starting continuous extraction...");
        System.out.println("[VICORE-DUMPER] Output: " + OUTPUT_PATH);
        
        // Start a background thread that runs every 5 seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                dumpNewStrings();
            } catch (Exception e) {
                // Ignore errors
            }
        }, 5, 5, TimeUnit.SECONDS); // Run every 5 seconds
        
        // Also add shutdown hook to save final results
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[VICORE-DUMPER] Final count: " + totalStrings + " strings");
        }));
    }
    
    private void dumpNewStrings() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        
        // Find all loaded vicore classes
        Set<Class<?>> classes = findVicoreClasses(loader);
        
        boolean foundNew = false;
        
        for (Class<?> clazz : classes) {
            try {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && 
                        field.getType() == String[].class) {
                        
                        String key = clazz.getName() + "." + field.getName();
                        
                        // Skip if already captured
                        if (capturedKeys.contains(key)) continue;
                        
                        field.setAccessible(true);
                        String[] strings = (String[]) field.get(null);
                        
                        if (strings != null && strings.length > 0) {
                            // Append to file
                            try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_PATH, true))) {
                                out.println("=== " + key + " ===");
                                for (int i = 0; i < strings.length; i++) {
                                    if (strings[i] != null && !strings[i].isEmpty()) {
                                        out.println("[" + i + "] \"" + escape(strings[i]) + "\"");
                                        totalStrings++;
                                    }
                                }
                                out.println();
                            }
                            
                            capturedKeys.add(key);
                            foundNew = true;
                            System.out.println("[VICORE-DUMPER] Captured: " + key + " (" + strings.length + " strings)");
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (foundNew) {
            System.out.println("[VICORE-DUMPER] Total: " + totalStrings + " strings from " + capturedKeys.size() + " arrays");
        }
    }
    
    private Set<Class<?>> findVicoreClasses(ClassLoader loader) {
        Set<Class<?>> classes = new HashSet<>();
        
        try {
            // Method 1: Get all loaded classes
            Field classesField = ClassLoader.class.getDeclaredField("classes");
            classesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Vector<Class<?>> loadedClasses = (Vector<Class<?>>) classesField.get(loader);
            
            for (Class<?> c : loadedClasses) {
                if (c.getName().startsWith("vicore.addon")) {
                    classes.add(c);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Method 2: Try to load known classes
        String[] knownClasses = {
            "vicore.addon.main.Vicore",
            "vicore.addon.main.UltraCrystal",
            "vicore.addon.main.ElytraAura",
            "vicore.addon.main.MaceTotemFail",
            "vicore.addon.main.ArrowKiller",
            "vicore.addon.main.UltraAura",
            "vicore.addon.main.UltraSurround",
            "vicore.addon.main.UltraMine",
            "vicore.addon.main.UltraPhase",
            "vicore.addon.main.UltraTotem",
            "vicore.addon.main.SpongeAura",
            "vicore.addon.main.BetterPops",
            "vicore.addon.main.EntityRenders",
            "vicore.addon.main.BasePlace",
            "vicore.addon.main.AntiMine",
            "vicore.addon.main.AntiPop",
            "vicore.addon.main.GrimAirplace",
            "vicore.addon.main.IceFlooder",
            "vicore.addon.main.InventoryAssist",
            "vicore.addon.main.UltraRegear",
            "vicore.addon.main.CapeSync",
            "vicore.addon.main.DiscordRPC",
            "vicore.addon.main.FacingSetting",
            "vicore.addon.main.InventorySettings",
            "vicore.addon.main.RangeSetting",
            "vicore.addon.main.RaytraceSetting",
            "vicore.addon.main.RotationsSetting",
            "vicore.addon.main.ServerSetting",
            "vicore.addon.main.SwingSetting",
        };
        
        for (String className : knownClasses) {
            try {
                Class<?> c = Class.forName(className, true, loader);
                classes.add(c);
            } catch (Exception e) {
                // Class not loaded yet
            }
        }
        
        return classes;
    }
    
    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
