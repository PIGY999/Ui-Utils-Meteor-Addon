package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.util.Hand;

import java.util.ArrayDeque;
import java.util.Queue;

public class LegitChestSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgElytra = settings.createGroup("Elytra");

    private final Setting<Chestplate> chestplate = sgGeneral.add(new EnumSetting.Builder<Chestplate>()
        .name("chestplate")
        .description("Which type of chestplate to swap to.")
        .defaultValue(Chestplate.PreferNetherite)
        .build()
    );

    private final Setting<Boolean> stayOn = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-on")
        .description("Stays on and activates when you turn it off.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> closeInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("close-inventory")
        .description("Sends inventory close after swap.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> legitSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("legit-swap")
        .description("Visibly swaps to the item in hotbar before equipping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapToItemDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-to-item-delay")
        .description("Delay in ms after selecting the item in hotbar before equipping.")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .visible(legitSwap::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to the original item after equipping.")
        .defaultValue(true)
        .visible(legitSwap::get)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ms before swapping back to the original item.")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .visible(() -> legitSwap.get() && swapBack.get())
        .build()
    );

    // Elytra settings
    private final Setting<Boolean> autoDeploy = sgElytra.add(new BoolSetting.Builder()
        .name("auto-deploy")
        .description("Automatically deploys elytra when equipped (only when swapping from chestplate).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoRocket = sgElytra.add(new BoolSetting.Builder()
        .name("auto-rocket")
        .description("Automatically fires a rocket after elytra deploys.")
        .defaultValue(false)
        .visible(autoDeploy::get)
        .build()
    );

    private final Setting<Integer> rocketSwapBackDelay = sgElytra.add(new IntSetting.Builder()
        .name("rocket-swap-back-delay")
        .description("Delay in ms before swapping back after firing rocket.")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .visible(() -> autoDeploy.get() && autoRocket.get())
        .build()
    );

    public LegitChestSwap() {
        super(AddonTemplate.CATEGORY, "legit-chest-swap", "Visibly swaps between chestplate and elytra.");
    }

    private enum State {
        IDLE,
        WAITING_TO_EQUIP,
        WAITING_TO_SWAP_BACK,
        DEPLOYING_ELYTRA,
        WAITING_FOR_ELYTRA_DEPLOY,
        SWAPPING_TO_ROCKET,
        USING_ROCKET,
        ROCKET_SWAP_BACK
    }

    // Simple queue - just stores what to equip (true = elytra, false = chestplate)
    private final Queue<Boolean> swapQueue = new ArrayDeque<>();
    
    private State state = State.IDLE;
    private int anchorSlot = -1; // The slot we always return to
    private int targetSlot = -1;
    private int rocketSlot = -1;
    private long waitTime = 0;
    private boolean justSwappedToElytra = false;
    private int deployAttempts = 0;

    @Override
    public void onActivate() {
        // Determine what to equip and queue it
        ItemStack currentItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wantElytra;
        
        if (currentItem.contains(DataComponentTypes.GLIDER)) {
            wantElytra = false; // Have elytra, want chestplate
        } else if (currentItem.contains(DataComponentTypes.EQUIPPABLE) && 
                   currentItem.get(DataComponentTypes.EQUIPPABLE).slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId()) {
            wantElytra = true; // Have chestplate, want elytra
        } else {
            wantElytra = true; // Nothing, default to elytra
        }
        
        swapQueue.add(wantElytra);
        
        // If idle, capture anchor slot and start processing
        if (state == State.IDLE) {
            anchorSlot = mc.player.getInventory().getSelectedSlot();
        }
        // If not idle, we're already processing and will use the same anchor slot
    }

    @Override
    public void onDeactivate() {
        if (stayOn.get() && state == State.IDLE) {
            // Process one swap when toggling off in stay-on mode
            ItemStack currentItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            boolean wantElytra;
            
            if (currentItem.contains(DataComponentTypes.GLIDER)) {
                wantElytra = false;
            } else if (currentItem.contains(DataComponentTypes.EQUIPPABLE) && 
                       currentItem.get(DataComponentTypes.EQUIPPABLE).slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId()) {
                wantElytra = true;
            } else {
                wantElytra = true;
            }
            
            swapQueue.add(wantElytra);
            anchorSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!stayOn.get() && state != State.IDLE) {
            // Module turned off mid-swap - finish immediately
            // Complete the equip if we haven't yet
            if (state == State.WAITING_TO_EQUIP) {
                equip(targetSlot);
            }
            // Stop using rocket if in progress
            if (state == State.USING_ROCKET) {
                mc.options.useKey.setPressed(false);
            }
            // Swap back to anchor slot immediately
            if (anchorSlot >= 0) {
                InvUtils.swap(anchorSlot, false);
            }
            // Clear queue and reset
            swapQueue.clear();
            reset();
        }
    }

    private void processQueue() {
        if (state != State.IDLE || swapQueue.isEmpty()) return;
        
        // Get next request
        boolean wantElytra = swapQueue.poll();
        
        // Check what we are CURRENTLY wearing before the swap
        ItemStack currentArmor = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean currentlyHasElytra = currentArmor.contains(DataComponentTypes.GLIDER);
        boolean currentlyHasChestplate = currentArmor.contains(DataComponentTypes.EQUIPPABLE) && 
                                          currentArmor.get(DataComponentTypes.EQUIPPABLE).slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId();
        
        // Auto-deploy should only happen when swapping FROM chestplate TO elytra
        // (not from nothing to elytra, not when already having elytra)
        justSwappedToElytra = wantElytra && currentlyHasChestplate;
        deployAttempts = 0;
        
        // Try to find the item
        FindItemResult result = wantElytra ? findElytraInHotbar() : findChestplateInHotbar();
        
        if (!result.found() || !result.isHotbar()) {
            // Try opposite
            result = wantElytra ? findChestplateInHotbar() : findElytraInHotbar();
            if (!result.found() || !result.isHotbar()) {
                // Nothing found, skip and check if we should toggle off
                if (!stayOn.get() && swapQueue.isEmpty()) {
                    toggle();
                }
                return;
            }
            // We found the opposite item, so we're not swapping to what we intended
            // Don't trigger auto-deploy in this case
            justSwappedToElytra = false;
        }

        targetSlot = result.slot();

        if (legitSwap.get()) {
            // Visually swap to the item
            InvUtils.swap(targetSlot, false);
            state = State.WAITING_TO_EQUIP;
            waitTime = System.currentTimeMillis() + swapToItemDelay.get();
        } else {
            // Silent swap
            equip(targetSlot);
            handlePostEquip();
        }
    }

    private void handlePostEquip() {
        // Check if we just equipped elytra and auto-deploy is enabled
        if (justSwappedToElytra && autoDeploy.get()) {
            state = State.DEPLOYING_ELYTRA;
            deployAttempts = 0;
            waitTime = System.currentTimeMillis() + 50; // Small delay before attempting deploy
        } else {
            finishSwap();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Always process queue if idle
        if (state == State.IDLE) {
            processQueue();
            return;
        }

        long now = System.currentTimeMillis();

        switch (state) {
            case WAITING_TO_EQUIP:
                if (now >= waitTime) {
                    // Time to equip
                    equip(targetSlot);
                    
                    if (legitSwap.get() && swapBack.get()) {
                        state = State.WAITING_TO_SWAP_BACK;
                        waitTime = now + swapBackDelay.get();
                    } else {
                        // Done - swap back to anchor immediately or stay on item
                        if (legitSwap.get()) {
                            InvUtils.swap(anchorSlot, false);
                        }
                        handlePostEquip();
                    }
                }
                break;

            case WAITING_TO_SWAP_BACK:
                if (now >= waitTime) {
                    // Swap back to anchor slot
                    InvUtils.swap(anchorSlot, false);
                    handlePostEquip();
                }
                break;

            case DEPLOYING_ELYTRA:
                if (now >= waitTime) {
                    // Try to deploy elytra
                    if (tryDeployElytra()) {
                        // Elytra deployed successfully
                        if (autoRocket.get()) {
                            // Find rocket and proceed to fire it
                            FindItemResult rocketResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
                            if (rocketResult.found() && rocketResult.isHotbar()) {
                                rocketSlot = rocketResult.slot();
                                state = State.SWAPPING_TO_ROCKET;
                            } else {
                                // No rocket found, just finish
                                finishSwap();
                            }
                        } else {
                            // No rocket needed, finish
                            finishSwap();
                        }
                    } else {
                        // Deploy failed, retry a few times
                        deployAttempts++;
                        if (deployAttempts < 10) {
                            waitTime = now + 50; // Retry in 50ms
                        } else {
                            // Give up after 10 attempts
                            finishSwap();
                        }
                    }
                }
                break;

            case SWAPPING_TO_ROCKET:
                // Visually swap to rocket
                InvUtils.swap(rocketSlot, false);
                state = State.USING_ROCKET;
                waitTime = System.currentTimeMillis() + 50; // Small delay before using
                break;

            case USING_ROCKET:
                if (now >= waitTime) {
                    // Use the rocket
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    
                    // Schedule swap back
                    state = State.ROCKET_SWAP_BACK;
                    waitTime = now + rocketSwapBackDelay.get();
                }
                break;

            case ROCKET_SWAP_BACK:
                if (now >= waitTime) {
                    // Swap back to anchor
                    InvUtils.swap(anchorSlot, false);
                    finishSwap();
                }
                break;

            case WAITING_FOR_ELYTRA_DEPLOY:
                // Fallback state if we were just waiting
                if (now >= waitTime) {
                    finishSwap();
                }
                break;
        }
    }

    private boolean tryDeployElytra() {
        if (mc.player == null) return false;
        
        // Check if already flying
        if (mc.player.isGliding()) return true;
        
        // Check if elytra is equipped
        ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chestItem.contains(DataComponentTypes.GLIDER)) return false;
        
        // To deploy elytra, we need to:
        // 1. Be in the air (not on ground)
        // 2. Send the START_FALL_FLYING packet
        
        // If on ground, jump first
        if (mc.player.isOnGround()) {
            mc.player.jump();
            return false; // Not deployed yet, need to retry
        }
        
        // If in air (or just jumped), send the deploy packet
        if (!mc.player.isOnGround()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true; // Assume it worked, will retry if not
        }
        
        return false;
    }

    private void finishSwap() {
        reset();
        
        // If queue has more items, process them with the same anchor slot
        if (!swapQueue.isEmpty()) {
            processQueue();
        } else if (!stayOn.get()) {
            // No more items and not stay-on, toggle off
            toggle();
        }
    }
    
    private void reset() {
        state = State.IDLE;
        targetSlot = -1;
        rocketSlot = -1;
        waitTime = 0;
        justSwappedToElytra = false;
        deployAttempts = 0;
    }

    private FindItemResult findElytraInHotbar() {
        return InvUtils.findInHotbar(itemStack -> itemStack.contains(DataComponentTypes.GLIDER));
    }

    private FindItemResult findChestplateInHotbar() {
        return InvUtils.findInHotbar(itemStack -> {
            Item item = itemStack.getItem();
            switch (chestplate.get()) {
                case Diamond:
                    return item == Items.DIAMOND_CHESTPLATE;
                case Netherite:
                    return item == Items.NETHERITE_CHESTPLATE;
                case PreferDiamond:
                    return item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE;
                case PreferNetherite:
                    return item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE;
            }
            return false;
        });
    }

    private void equip(int slot) {
        InvUtils.move().from(slot).toArmor(2);
        if (closeInventory.get()) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    @Override
    public void sendToggledMsg() {
        if (stayOn.get()) super.sendToggledMsg();
        else if (Config.get().chatFeedback.get() && chatFeedback) info("Triggered (highlight)%s(default).", title);
    }

    public enum Chestplate {
        Diamond,
        Netherite,
        PreferDiamond,
        PreferNetherite
    }
}
