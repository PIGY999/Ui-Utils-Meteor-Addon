package com.example.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;

import java.util.HashMap;
import java.util.Map;

/**
 * A prefix command that moves the player in a specified direction for a given amount of time.
 * Usage: .move <direction> <milliseconds>
 * 
 * Directions: forward, backward, left, right
 * Time: milliseconds to move into that direction
 */
public class MoveCommand extends Command {
    
    // Static map to track active movements: direction -> endTime in ms
    private static final Map<String, Long> activeMovements = new HashMap<>();
    private static boolean subscribed = false;

    public MoveCommand() {
        super("move", "Moves the player in a direction for a specified time.");
        
        // Subscribe to tick events once
        if (!subscribed) {
            MeteorClient.EVENT_BUS.subscribe(MoveCommand.class);
            subscribed = true;
        }
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("forward")
            .then(argument("milliseconds", IntegerArgumentType.integer(1))
                .executes(context -> {
                    int time = IntegerArgumentType.getInteger(context, "milliseconds");
                    startMovement("forward", time);
                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(literal("backward")
            .then(argument("milliseconds", IntegerArgumentType.integer(1))
                .executes(context -> {
                    int time = IntegerArgumentType.getInteger(context, "milliseconds");
                    startMovement("backward", time);
                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(literal("left")
            .then(argument("milliseconds", IntegerArgumentType.integer(1))
                .executes(context -> {
                    int time = IntegerArgumentType.getInteger(context, "milliseconds");
                    startMovement("left", time);
                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(literal("right")
            .then(argument("milliseconds", IntegerArgumentType.integer(1))
                .executes(context -> {
                    int time = IntegerArgumentType.getInteger(context, "milliseconds");
                    startMovement("right", time);
                    return SINGLE_SUCCESS;
                })
            )
        );
    }

    private void startMovement(String direction, int milliseconds) {
        long endTime = System.currentTimeMillis() + milliseconds;
        synchronized (activeMovements) {
            activeMovements.put(direction, endTime);
        }
        info("Moving " + direction + " for " + milliseconds + "ms");
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (activeMovements.isEmpty()) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        
        synchronized (activeMovements) {
            // Remove expired movements
            activeMovements.entrySet().removeIf(entry -> entry.getValue() <= currentTime);
            if (activeMovements.isEmpty()) return;

            // Calculate movement based on player rotation
            float yaw = mc.player.getYaw();
            double rad = Math.toRadians(yaw);
            
            double x = 0;
            double z = 0;
            
            for (String direction : activeMovements.keySet()) {
                switch (direction) {
                    case "forward" -> {
                        x -= Math.sin(rad);
                        z += Math.cos(rad);
                    }
                    case "backward" -> {
                        x += Math.sin(rad);
                        z -= Math.cos(rad);
                    }
                    case "left" -> {
                        x += Math.cos(rad);
                        z += Math.sin(rad);
                    }
                    case "right" -> {
                        x -= Math.cos(rad);
                        z -= Math.sin(rad);
                    }
                }
            }

            // Normalize and apply speed
            double length = Math.sqrt(x * x + z * z);
            if (length > 0) {
                x /= length;
                z /= length;
                
                // Apply movement speed (walking speed is about 0.1 blocks/tick)
                double speed = mc.player.getMovementSpeed() * 3.5;
                mc.player.setVelocity(x * speed, mc.player.getVelocity().y, z * speed);
                mc.player.setSprinting(speed > 0);
            }
        }
    }
}
