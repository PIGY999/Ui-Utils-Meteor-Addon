package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

/**
 * Utility class to send chat messages and commands via packets.
 * Works on servers with chat signing disabled (offline/cracked servers).
 */
public class ChatPacketSender {
    
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    
    /**
     * Sends a chat message to the server.
     * Works even when chat is disabled in client options (on non-premium servers).
     * 
     * This uses the high-level network handler method which still sends packets
     * even when the chat UI is disabled.
     * 
     * @param message The message to send
     */
    public static void sendMessage(String message) {
        if (mc.getNetworkHandler() == null) return;
        
        // Use sendChatMessage - this sends the packet directly
        // On servers with signing disabled, this works even with chat disabled
        mc.getNetworkHandler().sendChatMessage(message);
    }
    
    /**
     * Sends a command to the server.
     * Works even when chat is disabled in client options.
     * 
     * @param command The command without the leading slash
     */
    public static void sendCommand(String command) {
        if (mc.getNetworkHandler() == null) return;
        
        // Use sendChatCommand - this sends command packets directly
        // Commands work even when chat is disabled
        mc.getNetworkHandler().sendChatCommand(command);
    }
}
