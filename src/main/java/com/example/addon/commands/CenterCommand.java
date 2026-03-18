package com.example.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * A prefix command that centers the player on the current block.
 * The center of a block is at X+0.5 and Z+0.5.
 * Optionally accepts a direction to set yaw (north, east, south, west).
 * Always sets pitch to 0.
 * 
 * Usage: .center [direction]
 * 
 * Directions:
 *   north: yaw 180
 *   east:  yaw -90
 *   south: yaw 0
 *   west:  yaw 90
 */
public class CenterCommand extends Command {

    public CenterCommand() {
        super("center", "Centers the player on the current block. Optional direction parameter sets yaw.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Basic .center without direction
        builder.executes(context -> {
            centerPlayer(null);
            return SINGLE_SUCCESS;
        });

        // .center north - yaw 180
        builder.then(literal("north").executes(context -> {
            centerPlayer(180f);
            return SINGLE_SUCCESS;
        }));

        // .center east - yaw -90
        builder.then(literal("east").executes(context -> {
            centerPlayer(-90f);
            return SINGLE_SUCCESS;
        }));

        // .center south - yaw 0
        builder.then(literal("south").executes(context -> {
            centerPlayer(0f);
            return SINGLE_SUCCESS;
        }));

        // .center west - yaw 90
        builder.then(literal("west").executes(context -> {
            centerPlayer(90f);
            return SINGLE_SUCCESS;
        }));
    }

    private void centerPlayer(Float yaw) {
        if (mc.player == null) {
            error("Player is null.");
            return;
        }

        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();
        
        // Calculate the center of the block
        double centerX = Math.floor(currentX) + 0.5;
        double centerZ = Math.floor(currentZ) + 0.5;
        
        // Check if already centered (within a small threshold) when no yaw change
        double threshold = 0.01;
        boolean alreadyCentered = Math.abs(currentX - centerX) < threshold && Math.abs(currentZ - centerZ) < threshold;
        
        if (yaw == null) {
            // No direction specified, only center position
            if (alreadyCentered) {
                info("Already centered.");
                return;
            }
            mc.player.setPosition(centerX, mc.player.getY(), centerZ);
            info(String.format("Centered: %.2f, %.2f -> %.2f, %.2f", currentX, currentZ, centerX, centerZ));
        } else {
            // Direction specified, center and set rotation
            mc.player.setPosition(centerX, mc.player.getY(), centerZ);
            mc.player.setYaw(yaw);
            mc.player.setPitch(0f);
            
            String directionName = getDirectionName(yaw);
            if (alreadyCentered) {
                info("Already centered. Facing " + directionName + ".");
            } else {
                info(String.format("Centered and facing %s: %.2f, %.2f -> %.2f, %.2f", directionName, currentX, currentZ, centerX, centerZ));
            }
        }
    }
    
    private String getDirectionName(float yaw) {
        // Normalize yaw to 0-360 range
        float normalized = ((yaw % 360) + 360) % 360;
        
        if (normalized == 180f) return "north";
        if (normalized == 270f || normalized == -90f) return "east";
        if (normalized == 0f || normalized == 360f) return "south";
        if (normalized == 90f) return "west";
        return String.valueOf(yaw);
    }
}
