package com.example.addon.commands;

import com.example.addon.utils.SyncManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SyncCommand extends Command {
    public SyncCommand() {
        super("ready", "Marks this client as ready for synced clicking.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("clientId", IntegerArgumentType.integer(1, 2)).executes(context -> {
            int clientId = IntegerArgumentType.getInteger(context, "clientId");
            executeCommand(clientId);
            return SINGLE_SUCCESS;
        }));
    }

    private void executeCommand(int clientId) {
        BlockPos closestSign = null;
        double closestDist = Double.MAX_VALUE;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockEntity blockEntity = mc.world.getBlockEntity(pos);
                    if (blockEntity instanceof SignBlockEntity) {
                        double dist = mc.player.squaredDistanceTo(pos.toCenterPos());
                        if (dist <= 16.0 && dist < closestDist) {
                            closestDist = dist;
                            closestSign = pos;
                        }
                    }
                }
            }
        }

        if (closestSign == null) {
            ChatUtils.info("No sign found within 4 blocks.");
            return;
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(closestSign.toCenterPos(), Direction.UP, closestSign, false));
        
        // Polling container open logic would ideally need mixins or events.
        // Simplified polling for the example
        new Thread(() -> {
            try {
                ChatUtils.info("Waiting for GUI and Chest to load...");
                long startTime = System.currentTimeMillis();
                long timeout = 5000; // 5 seconds timeout
                boolean foundChest = false;

                while (System.currentTimeMillis() - startTime < timeout) {
                    if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                        ScreenHandler handler = mc.player.currentScreenHandler;
                        for (int i = 0; i < handler.slots.size(); i++) {
                            if (handler.getSlot(i).getStack().getItem() == Items.CHEST) {
                                foundChest = true;
                                break;
                            }
                        }

                        if (foundChest) {
                            ChatUtils.info("Found chest! Reading state.");
                            SyncManager.SyncState state = SyncManager.readState();
                            if (clientId == 1) state.client1_ready = true;
                            else state.client2_ready = true;

                            if (state.client1_ready && state.client2_ready) {
                                state.execute_timestamp = System.currentTimeMillis() + 5000; // 5000ms delay for sync
                                ChatUtils.info("BOTH READY! Generated timestamp (5s).");
                            } else {
                                ChatUtils.info("Ready. Waiting for other client...");
                            }
                            
                            state.completed = false;
                            SyncManager.writeState(state);
                            break; // Exit the loop since we found it
                        }
                    }
                    Thread.sleep(100); // Check every 100ms
                }

                if (!foundChest) {
                    ChatUtils.info("Timed out (5s) waiting for GUI or Chest to load.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
