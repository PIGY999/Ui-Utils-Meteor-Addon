package com.example.addon.modules;

import com.example.addon.utils.SyncManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

public class SyncClicker extends Module {
    public SyncClicker() {
        super(Categories.Misc, "sync-clicker", "Synchronized clicking for multiple instances.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;

        SyncManager.SyncState state = SyncManager.readState();
        if (state.client1_ready && state.client2_ready && !state.completed && state.execute_timestamp > 0) {
            long currentTime = System.currentTimeMillis();
            long timeDiff = state.execute_timestamp - currentTime;

            if (timeDiff <= 20) {
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
                    InvUtils.quickMove().slotId(chestTarget);
                }

                // Reset states safely
                SyncManager.SyncState finalState = SyncManager.readState();
                finalState.client1_ready = false;
                finalState.client2_ready = false;
                finalState.execute_timestamp = 0;
                finalState.completed = true;
                SyncManager.writeState(finalState);
            }
        }
    }
}