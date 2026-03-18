package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
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
    private static final int DIAMOND_MIN_Y = 17;
    private static final int BUDDING_MIN_Y = 55;
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

    private final Setting<SettingColor> rawCopperColor = sgRender.add(new ColorSetting.Builder()
        .name("raw-copper-color")
        .description("Render color for raw copper protections (15x15).")
        .defaultValue(new SettingColor(255, 140, 90, 160))
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
            event.renderer.box(box, area.color, area.color, ShapeMode.Lines, 0);
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
                    if (block == Blocks.RAW_COPPER_BLOCK && pos.getY() > RAW_COPPER_MIN_Y) {
                        addArea(pos, 15, rawCopperColor.get());
                    } else if (block == Blocks.LIME_GLAZED_TERRACOTTA) {
                        addArea(pos, 50, limeGlazedColor.get());
                    } else if (block == Blocks.NETHER_WART_BLOCK) {
                        addArea(pos, 100, netherWartColor.get());
                    } else if (block == Blocks.DIAMOND_ORE && pos.getY() > DIAMOND_MIN_Y) {
                        addArea(pos, 160, diamondOreColor.get());
                    } else if (block == Blocks.BUDDING_AMETHYST && pos.getY() > BUDDING_MIN_Y) {
                        addArea(pos, 250, buddingAmethystColor.get());
                    }
                }
            }
        }
    }

    private void addArea(BlockPos pos, int radius, Color color) {
        areas.add(new ProtectionArea(pos.toImmutable(), radius, color));
    }

    private static class ProtectionArea {
        private final BlockPos center;
        private final int radius;
        private final Color color;

        private ProtectionArea(BlockPos center, int radius, Color color) {
            this.center = center;
            this.radius = radius;
            this.color = color;
        }
    }
}
