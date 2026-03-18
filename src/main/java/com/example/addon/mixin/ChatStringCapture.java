package com.example.addon.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
import java.util.*;

/**
 * Captures ALL chat messages to extract Vicore strings.
 * This catches the decrypted strings as they're actually used!
 */
@Mixin(ChatHud.class)
public class ChatStringCapture {
    
    private static final String OUTPUT_PATH = System.getProperty("user.home") + "\\Desktop\\vicore_chat_strings.txt";
    private static Set<String> capturedStrings = new HashSet<>();
    private static PrintWriter logFile;
    
    static {
        try {
            logFile = new PrintWriter(new FileWriter(OUTPUT_PATH, true));
            System.out.println("[CHAT-CAPTURE] Logging to: " + OUTPUT_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        try {
            String text = message.getString();
            
            // Capture ALL Vicoré messages
            if (text.contains("Vicoré") || text.contains("[Vicoré]")) {
                if (capturedStrings.add(text)) {
                    logFile.println(text);
                    logFile.flush();
                    System.out.println("[CHAT-CAPTURE] " + text);
                }
            }
            
            // Also capture any message with module names from the addon
            String[] moduleNames = {
                "UltraCrystal", "ElytraAura", "MaceTotemFail", "ArrowKiller", 
                "UltraAura", "UltraSurround", "UltraMine", "UltraPhase",
                "UltraTotem", "SpongeAura", "BetterPops", "EntityRenders",
                "BasePlace", "AntiMine", "AntiPop", "GrimAirplace",
                "IceFlooder", "InventoryAssist", "UltraRegear", "CapeSync",
                "DiscordRPC", "FacingSetting", "InventorySettings", "RangeSetting",
                "RaytraceSetting", "RotationsSetting", "ServerSetting", "SwingSetting"
            };
            
            for (String module : moduleNames) {
                if (text.contains(module) && capturedStrings.add(text)) {
                    logFile.println("[" + module + "] " + text);
                    logFile.flush();
                    System.out.println("[CHAT-CAPTURE] " + text);
                }
            }
            
        } catch (Exception e) {
            // Ignore
        }
    }
}
