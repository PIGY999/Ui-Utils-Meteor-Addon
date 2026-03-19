package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class ProtectionAreaRenderer extends Module {
    private static final int RAW_COPPER_MIN_Y = 55;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Horizontal scan radius around the player.")
        .defaultValue(96)
        .min(16)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> scanHeight = sgGeneral.add(new IntSetting.Builder()
        .name("scan-height")
        .description("Vertical scan range above and below the player.")
        .defaultValue(64)
        .min(16)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> scanIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval-ticks")
        .description("How often to rescan for protection blocks.")
        .defaultValue(20)
        .min(5)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> isolationRadius = sgGeneral.add(new IntSetting.Builder()
        .name("isolation-radius")
        .description("Blocks of the same type within this radius will not count as protections.")
        .defaultValue(8)
        .min(5)
        .sliderMax(10)
        .build()
    );

    private final Setting<SettingColor> rawCopperColor = sgRender.add(new ColorSetting.Builder()
        .name("raw-copper-color")
        .description("Render color for raw copper protections (15x15).")
        .defaultValue(new SettingColor(255, 140, 90, 160))
        .build()
    );

    private final Setting<Boolean> renderBlockOutline = sgRender.add(new BoolSetting.Builder()
        .name("render-block-outline")
        .description("Render an outline on the protection block itself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderAreaFilled = sgRender.add(new BoolSetting.Builder()
        .name("render-area-filled")
        .description("Render full boxes (lines + fill) for protection areas.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> limeGlazedColor = sgRender.add(new ColorSetting.Builder()
        .name("lime-glazed-color")
        .description("Render color for lime glazed terracotta protections (50x50).")
        .defaultValue(new SettingColor(140, 255, 120, 160))
        .build()
    );

    private final Setting<SettingColor> netherWartColor = sgRender.add(new ColorSetting.Builder()
        .name("nether-wart-color")
        .description("Render color for nether wart protections (100x100).")
        .defaultValue(new SettingColor(170, 30, 40, 160))
        .build()
    );

    private final Setting<SettingColor> diamondOreColor = sgRender.add(new ColorSetting.Builder()
        .name("diamond-ore-color")
        .description("Render color for diamond ore protections (160x160).")
        .defaultValue(new SettingColor(120, 220, 255, 160))
        .build()
    );

    private final Setting<SettingColor> buddingAmethystColor = sgRender.add(new ColorSetting.Builder()
        .name("budding-amethyst-color")
        .description("Render color for budding amethyst protections (250x250).")
        .defaultValue(new SettingColor(200, 130, 255, 160))
        .build()
    );

    private final List<ProtectionArea> areas = new ArrayList<>();
    private int ticksSinceScan = 0;

    public ProtectionAreaRenderer() {
        super(AddonTemplate.CATEGORY, "protection-area-render", "Renders protection boundaries from protection blocks.");
    }

    @Override
    public void onDeactivate() {
        areas.clear();
        ticksSinceScan = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        ticksSinceScan++;
        if (ticksSinceScan < scanIntervalTicks.get()) return;
        ticksSinceScan = 0;

        rescanProtections();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (mc.world == null || areas.isEmpty()) return;

        for (ProtectionArea area : areas) {
            Box box = new Box(area.center).expand(area.radius, 0, area.radius);
            ShapeMode shapeMode = renderAreaFilled.get() ? ShapeMode.Both : ShapeMode.Lines;
            event.renderer.box(box, area.color, area.color, shapeMode, 0);
            if (renderBlockOutline.get()) {
                Box blockBox = new Box(area.center);
                event.renderer.box(blockBox, area.color, area.color, ShapeMode.Lines, 0);
            }
        }
    }

    private void rescanProtections() {
        areas.clear();

        int radius = scanRadius.get();
        int height = scanHeight.get();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -height; dy <= height; dy++) {
                    pos.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!mc.world.isInBuildLimit(pos)) continue;

                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block == Blocks.RAW_COPPER_BLOCK && pos.getY() > RAW_COPPER_MIN_Y
                        && isIsolated(block, pos, isolationRadius.get())) {
                        addArea(pos, sideToRadius(15), rawCopperColor.get());
                    } else if (block == Blocks.LIME_GLAZED_TERRACOTTA
                        && isIsolated(block, pos, isolationRadius.get())) {
                        addArea(pos, sideToRadius(50), limeGlazedColor.get());
                    } else if (block == Blocks.NETHER_WART_BLOCK
                        && isIsolated(block, pos, isolationRadius.get())) {
                        addArea(pos, sideToRadius(100), netherWartColor.get());
                    } else if (block == Blocks.DIAMOND_ORE
                        && isIsolatedDiamond(pos, isolationRadius.get())) {
                        addArea(pos, sideToRadius(160), diamondOreColor.get());
                    } else if (block == Blocks.BUDDING_AMETHYST
                        && isIsolated(block, pos, isolationRadius.get())) {
                        addArea(pos, sideToRadius(250), buddingAmethystColor.get());
                    }
                }
            }
        }
    }

    private boolean isIsolated(Block block, BlockPos pos, int radius) {
        BlockPos.Mutable checkPos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    checkPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!mc.world.isInBuildLimit(checkPos)) continue;
                    if (mc.world.getBlockState(checkPos).getBlock() == block) return false;
                }
            }
        }
        return true;
    }

    private boolean isIsolatedDiamond(BlockPos pos, int radius) {
        BlockPos.Mutable checkPos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    checkPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!mc.world.isInBuildLimit(checkPos)) continue;
                    if (isDiamondOre(mc.world.getBlockState(checkPos).getBlock())) return false;
                }
            }
        }
        return true;
    }

    private static boolean isDiamondOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE;
    }

    private void addArea(BlockPos pos, double radius, Color color) {
        areas.add(new ProtectionArea(pos.toImmutable(), radius, color));
    }

    private static double sideToRadius(int sideLength) {
        return (sideLength - 1) / 2.0;
    }

    private static class ProtectionArea {
        private final BlockPos center;
        private final double radius;
        private final Color color;

        private ProtectionArea(BlockPos center, double radius, Color color) {
            this.center = center;
            this.radius = radius;
            this.color = color;
        }
    }
}
