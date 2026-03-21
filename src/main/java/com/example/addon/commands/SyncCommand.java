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

        for (BlockEntity blockEntity : mc.world.blockEntities) {
            if (blockEntity instanceof SignBlockEntity) {
                double dist = mc.player.squaredDistanceTo(blockEntity.getPos().toCenterPos());
                if (dist <= 16.0 && dist < closestDist) {
                    closestDist = dist;
                    closestSign = blockEntity.getPos();
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
                Thread.sleep(250); // Wait for GUI
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    ScreenHandler handler = mc.player.currentScreenHandler;
                    boolean hasChest = false;
                    for (int i = 0; i < handler.slots.size(); i++) {
                        if (handler.getSlot(i).getStack().getItem() == Items.CHEST) {
                            hasChest = true;
                            break;
                        }
                    }

                    if (hasChest) {
                        SyncManager.SyncState state = SyncManager.readState();
                        if (clientId == 1) state.client1_ready = true;
                        else state.client2_ready = true;

                        if (state.client1_ready && state.client2_ready) {
                            state.execute_timestamp = System.currentTimeMillis() + 500;
                        }
                        
                        state.completed = false;
                        SyncManager.writeState(state);
                        ChatUtils.info("Client marked as ready.");
                    } else {
                        ChatUtils.info("No chest found in container.");
                    }
                }
            } catch (Exception e) {}
        }).start();
    }
}
