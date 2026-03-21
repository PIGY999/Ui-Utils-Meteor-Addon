package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public class AutoVclip extends Module {
    private static final float LOOK_UP_PITCH = -45.0f;
    private static final float LOOK_DOWN_PITCH = 45.0f;

    public AutoVclip() {
        super(AddonTemplate.CATEGORY, "auto-vclip", "VClips to the nearest cavity above or below based on look direction.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        float pitch = mc.player.getPitch();
        int targetY;
        if (pitch <= LOOK_UP_PITCH) {
            targetY = findNearestCavityAbove();
        } else if (pitch >= LOOK_DOWN_PITCH) {
            targetY = findNearestCavityBelow();
        } else {
            ChatUtils.warning("AutoVclip: Look up or down to choose direction.");
            toggle();
            return;
        }

        if (targetY == Integer.MIN_VALUE) {
            ChatUtils.warning("AutoVclip: No cavity found in that direction.");
            toggle();
            return;
        }

        double deltaY = targetY - mc.player.getY();
        if (Math.abs(deltaY) < 0.01) {
            ChatUtils.warning("AutoVclip: Already in the nearest cavity.");
            toggle();
            return;
        }

        sendVclip(deltaY);
        ChatUtils.info("AutoVclip: Clipped to Y=" + targetY + ".");
        toggle();
    }

    private int findNearestCavityAbove() {
        int x = mc.player.getBlockPos().getX();
        int z = mc.player.getBlockPos().getZ();
        int topSurfaceBlockY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int startY = Math.max(mc.player.getBlockPos().getY() + 1, topSurfaceBlockY + 1);
        int topY = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 6;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = startY; y <= topY; y++) {
            if (isAirColumnAt(x, y, z, pos)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private int findNearestCavityBelow() {
        int x = mc.player.getBlockPos().getX();
        int z = mc.player.getBlockPos().getZ();
        int startY = mc.player.getBlockPos().getY() - 1;
        int bottomY = mc.world.getBottomY() + 1;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = startY; y >= bottomY; y--) {
            if (isCavityAt(x, y, z, pos)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isCavityAt(int x, int y, int z, BlockPos.Mutable pos) {
        BlockState feet = mc.world.getBlockState(pos.set(x, y, z));
        BlockState head = mc.world.getBlockState(pos.set(x, y + 1, z));
        BlockState below = mc.world.getBlockState(pos.set(x, y - 1, z));

        return feet.isAir() && head.isAir() && !below.isAir();
    }

    private boolean isAirColumnAt(int x, int y, int z, BlockPos.Mutable pos) {
        BlockState feet = mc.world.getBlockState(pos.set(x, y, z));
        BlockState head = mc.world.getBlockState(pos.set(x, y + 1, z));
        return feet.isAir() && head.isAir();
    }

    private void sendVclip(double blocks) {
        int packetsRequired = (int) Math.ceil(Math.abs(blocks / 10));
        if (packetsRequired > 20) packetsRequired = 1;

        if (mc.player.hasVehicle()) {
            for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
            }
            mc.player.getVehicle().setPosition(
                mc.player.getVehicle().getX(),
                mc.player.getVehicle().getY() + blocks,
                mc.player.getVehicle().getZ()
            );
            mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
        } else {
            for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
            }
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY() + blocks,
                mc.player.getZ(),
                true,
                mc.player.horizontalCollision
            ));
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + blocks, mc.player.getZ());
        }
    }
}
