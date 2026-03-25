package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.SyncManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

import net.minecraft.screen.slot.SlotActionType;

public class SyncClicker extends Module {
    private long lastExecutedTimestamp = 0;

    public SyncClicker() {
        super(AddonTemplate.CATEGORY, "sync-clicker", "Synchronized clicking for multiple instances.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;

        SyncManager.SyncState state = SyncManager.readState();
        if (state.client1_ready && state.client2_ready && !state.completed && state.execute_timestamp > 0) {

            // Prevent double firing inside the same client
            if (state.execute_timestamp == lastExecutedTimestamp) return;

            long currentTime = System.currentTimeMillis();
            long timeDiff = state.execute_timestamp - currentTime;

            if (timeDiff > 0 && timeDiff % 1000 == 0) {
                // Occasional tick print
            }

            // Expanded window slightly to 50ms so early ticks don't miss parsing it before the other client deletes it
            if (timeDiff <= 50) {
                ChatUtils.info("Executing fast click! Time diff: " + timeDiff);
                while (System.currentTimeMillis() < state.execute_timestamp) {
                    // Busy wait for high precision synchronization
                }

                // Execute exact click
                int chestTarget = -1;
                for (Slot slot : mc.player.currentScreenHandler.slots) {
                    if (slot.getStack().getItem() == Items.CHEST) {
                        chestTarget = slot.id;
                        break;
                    }
                }

                if (chestTarget != -1) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, chestTarget, 0, SlotActionType.QUICK_MOVE, mc.player);
                    ChatUtils.info("Clicked chest on slot " + chestTarget + "!");
                } else {
                    ChatUtils.info("Chest not found during click attempt.");
                }

                lastExecutedTimestamp = state.execute_timestamp;
                long executedTime = state.execute_timestamp;

                // Delay resetting the JSON state by 1 second so the OTHER client has time to read the timestamp and also click!
                new Thread(() -> {
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    SyncManager.SyncState finalState = SyncManager.readState();
                    if (finalState.execute_timestamp == executedTime) {
                        finalState.client1_ready = false;
                        finalState.client2_ready = false;
                        finalState.execute_timestamp = 0;
                        finalState.completed = true;
                        SyncManager.writeState(finalState);
                    }
                }).start();
            }
        }
    }
}
