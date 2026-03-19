package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.MeshBuilderVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.SimpleBlockRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extended StorageESP module with logging and opened container tracking.
 * Requires StorageESP to be active.
 * Only renders opened containers with a highlight color.
 */
public class HuntStorageEsp extends Module {
    private static final MatrixStack MATRICES = new MatrixStack();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgRender = settings.getDefaultGroup();
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    // Track opened containers per server/world
    // Key: "serverIp:worldName" or "singleplayer:worldName", Value: set of "x,y,z" positions
    private final Map<String, Set<String>> serverOpenedContainers = new HashMap<>();
    private final Set<String> loggedContainers = new HashSet<>();
    private String currentServerKey = null;

    // Render settings
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> openedColor = sgRender.add(new ColorSetting.Builder()
        .name("opened-color")
        .description("Color for opened containers.")
        .defaultValue(new SettingColor(0, 255, 0, 180))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracers to opened containers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> fadeDistance = sgRender.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("The distance at which the color will fade.")
        .defaultValue(6)
        .min(0)
        .sliderMax(12)
        .build()
    );

    // Logging settings
    private final Setting<Boolean> enableLogging = sgLogging.add(new BoolSetting.Builder()
        .name("enable-logging")
        .description("Log container interactions to a JSON file.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> logFilename = sgLogging.add(new StringSetting.Builder()
        .name("log-filename")
        .description("Name of the log file (without extension).")
        .defaultValue("hunt_storage_log")
        .visible(enableLogging::get)
        .build()
    );

    private final Color lineColor = new Color(0, 0, 0, 0);
    private final Color sideColor = new Color(0, 0, 0, 0);
    private boolean render;
    private int count;

    private final MeshBuilder mesh;
    private final MeshBuilderVertexConsumerProvider vertexConsumerProvider;

    public HuntStorageEsp() {
        super(AddonTemplate.CATEGORY, "hunt-storage-esp", "Highlights previously opened storage containers. Requires StorageESP.");

        mesh = new MeshBuilder(MeteorRenderPipelines.WORLD_COLORED);
        vertexConsumerProvider = new MeshBuilderVertexConsumerProvider(mesh);
    }

    @Override
    public void onActivate() {
        // Check if StorageESP is active - if not, disable ourselves
        if (!isStorageEspActive()) {
            toggle();
            info("StorageESP must be active to use HuntStorageESP.");
            return;
        }

        // Generate the current server/world key
        currentServerKey = getCurrentServerKey();

        // Load opened containers for this server/world
        loadOpenedContainers();
    }

    @Override
    public void onDeactivate() {
        saveOpenedContainers();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Auto-disable if StorageESP gets disabled
        if (!isStorageEspActive()) {
            toggle();
            info("Disabled because StorageESP was turned off.");
            return;
        }

        // Check if server/world changed
        String newServerKey = getCurrentServerKey();
        if (currentServerKey == null || !currentServerKey.equals(newServerKey)) {
            // Save old data
            saveOpenedContainers();
            // Load new data
            currentServerKey = newServerKey;
            serverOpenedContainers.clear();
            loggedContainers.clear();
            loadOpenedContainers();
        }
    }

    private boolean isStorageEspActive() {
        Module storageEsp = Modules.get().get("storage-esp");
        return storageEsp != null && storageEsp.isActive();
    }

    /**
     * Generates a unique key for the current server or singleplayer world.
     * Format: "server:address:world" or "singleplayer:worldName"
     */
    private String getCurrentServerKey() {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            // Multiplayer - use server address
            String serverAddress = mc.getCurrentServerEntry() != null
                ? mc.getCurrentServerEntry().address
                : "unknown";
            // Sanitize for filename safety
            serverAddress = serverAddress.replaceAll("[^a-zA-Z0-9.-]", "_");
            return "server:" + serverAddress;
        } else if (mc.isInSingleplayer()) {
            // Singleplayer - use world name
            String worldName = mc.getServer() != null && mc.getServer().getSaveProperties() != null
                ? mc.getServer().getSaveProperties().getLevelName()
                : Utils.getFileWorldName();
            return "singleplayer:" + worldName;
        }
        return "unknown:" + Utils.getFileWorldName();
    }

    /**
     * Gets the log file for the current world/server.
     */
    private File getLogFile() {
        return AddonTemplate.GetConfigFile("HuntStorageEsp", logFilename.get() + ".json");
    }

    /**
     * Loads previously opened containers from the log file.
     * Organized by server/world key.
     */
    private void loadOpenedContainers() {
        if (!enableLogging.get()) return;
        if (currentServerKey == null) return;

        File logFile = getLogFile();
        if (!logFile.exists()) return;

        try (FileReader reader = new FileReader(logFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            serverOpenedContainers.clear();

            // Load entries for current server
            if (root.has(currentServerKey)) {
                JsonArray array = root.getAsJsonArray(currentServerKey);
                Set<String> positions = new HashSet<>();

                for (int i = 0; i < array.size(); i++) {
                    JsonObject entry = array.get(i).getAsJsonObject();
                    if (entry.has("x") && entry.has("y") && entry.has("z")) {
                        String posKey = entry.get("x").getAsInt() + "," +
                                        entry.get("y").getAsInt() + "," +
                                        entry.get("z").getAsInt();
                        positions.add(posKey);
                        loggedContainers.add(posKey);
                    }
                }
                serverOpenedContainers.put(currentServerKey, positions);
            }
        } catch (IOException e) {
            AddonTemplate.LOG.error("Failed to load HuntStorageEsp log", e);
        }
    }

    /**
     * Saves all opened container positions to the JSON file.
     * Organized by server/world key.
     */
    private void saveOpenedContainers() {
        if (!enableLogging.get()) return;
        if (currentServerKey == null) return;

        File logFile = getLogFile();
        logFile.getParentFile().mkdirs();

        // Load existing data from file
        JsonObject root = new JsonObject();
        if (logFile.exists()) {
            try (FileReader reader = new FileReader(logFile)) {
                JsonObject loaded = GSON.fromJson(reader, JsonObject.class);
                if (loaded != null) {
                    root = loaded;
                }
            } catch (IOException e) {
                // File might be corrupted, start fresh
            }
        }

        // Get current server's entries
        Set<String> currentPositions = serverOpenedContainers.getOrDefault(currentServerKey, new HashSet<>());

        // Build array with metadata for current server
        JsonArray array = new JsonArray();

        // Load existing entries to preserve metadata
        Map<String, JsonObject> existingEntries = new HashMap<>();
        if (root.has(currentServerKey)) {
            JsonArray existingArray = root.getAsJsonArray(currentServerKey);
            for (int i = 0; i < existingArray.size(); i++) {
                JsonObject entry = existingArray.get(i).getAsJsonObject();
                if (entry.has("x") && entry.has("y") && entry.has("z")) {
                    String posKey = entry.get("x").getAsInt() + "," +
                                    entry.get("y").getAsInt() + "," +
                                    entry.get("z").getAsInt();
                    existingEntries.put(posKey, entry);
                }
            }
        }

        // Write all positions for current server
        for (String posKey : currentPositions) {
            if (existingEntries.containsKey(posKey)) {
                // Preserve existing entry with metadata
                array.add(existingEntries.get(posKey));
            } else {
                // Create new minimal entry
                String[] parts = posKey.split(",");
                if (parts.length == 3) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("x", Integer.parseInt(parts[0]));
                    entry.addProperty("y", Integer.parseInt(parts[1]));
                    entry.addProperty("z", Integer.parseInt(parts[2]));
                    entry.addProperty("timestamp", Instant.now().toString());
                    array.add(entry);
                }
            }
        }

        // Update root with current server's data
        root.add(currentServerKey, array);

        // Save back to file
        try (FileWriter writer = new FileWriter(logFile)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            AddonTemplate.LOG.error("Failed to save HuntStorageEsp log", e);
        }
    }

    /**
     * Gets the container type name for logging.
     */
    private String getContainerTypeName(BlockEntity blockEntity) {
        if (blockEntity instanceof TrappedChestBlockEntity) return "trapped_chest";
        if (blockEntity instanceof ChestBlockEntity) return "chest";
        if (blockEntity instanceof BarrelBlockEntity) return "barrel";
        if (blockEntity instanceof ShulkerBoxBlockEntity) return "shulker_box";
        if (blockEntity instanceof EnderChestBlockEntity) return "ender_chest";
        if (blockEntity instanceof FurnaceBlockEntity) return "furnace";
        if (blockEntity instanceof BlastFurnaceBlockEntity) return "blast_furnace";
        if (blockEntity instanceof SmokerBlockEntity) return "smoker";
        if (blockEntity instanceof DispenserBlockEntity) return "dispenser";
        if (blockEntity instanceof DropperBlockEntity) return "dropper";
        if (blockEntity instanceof HopperBlockEntity) return "hopper";
        if (blockEntity instanceof BrewingStandBlockEntity) return "brewing_stand";
        if (blockEntity instanceof ChiseledBookshelfBlockEntity) return "chiseled_bookshelf";
        if (blockEntity instanceof CrafterBlockEntity) return "crafter";
        if (blockEntity instanceof DecoratedPotBlockEntity) return "decorated_pot";
        return "unknown";
    }

    /**
     * Calculates the adjacent chest position for double chests.
     */
    private BlockPos getAdjacentChestPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) return null;

        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
        if (chestType == ChestType.SINGLE) return null;

        Direction facing = state.get(ChestBlock.FACING);
        Direction offsetDir = chestType == ChestType.LEFT
            ? facing.rotateYClockwise()
            : facing.rotateYCounterclockwise();

        return pos.offset(offsetDir);
    }

    /**
     * Verifies that the block at the given position is the complementary chest half.
     */
    private boolean isComplementaryChestHalf(BlockPos pos, ChestType expectedType) {
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;

        ChestType actualType = state.get(ChestBlock.CHEST_TYPE);
        return actualType == expectedType;
    }

    /**
     * Returns true if this container type should be tracked.
     */
    private boolean isTrackableContainer(BlockEntity blockEntity) {
        return blockEntity instanceof ChestBlockEntity
            || blockEntity instanceof TrappedChestBlockEntity
            || blockEntity instanceof BarrelBlockEntity
            || blockEntity instanceof ShulkerBoxBlockEntity
            || blockEntity instanceof EnderChestBlockEntity
            || blockEntity instanceof AbstractFurnaceBlockEntity
            || blockEntity instanceof BrewingStandBlockEntity
            || blockEntity instanceof ChiseledBookshelfBlockEntity
            || blockEntity instanceof CrafterBlockEntity
            || blockEntity instanceof DispenserBlockEntity
            || blockEntity instanceof DecoratedPotBlockEntity
            || blockEntity instanceof HopperBlockEntity;
    }

    /**
     * Checks if a trackable container actually exists at the given position.
     * Returns false if the block was destroyed or changed.
     */
    private boolean hasContainerAt(BlockPos pos) {
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);
        return isTrackableContainer(blockEntity);
    }

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        BlockPos pos = event.result.getBlockPos();
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);

        if (blockEntity == null) return;
        if (!isTrackableContainer(blockEntity)) return;

        BlockState state = blockEntity.getCachedState();
        String posKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();

        // Check if this container was already opened
        Set<String> currentContainers = serverOpenedContainers.computeIfAbsent(currentServerKey, k -> new HashSet<>());
        boolean wasAlreadyOpened = currentContainers.contains(posKey);

        // Mark this position as opened
        currentContainers.add(posKey);

        // Handle double chest logic - mark both halves as opened
        if (blockEntity instanceof ChestBlockEntity) {
            ChestType chestType = state.get(ChestBlock.CHEST_TYPE);

            if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                BlockPos otherHalfPos = getAdjacentChestPos(pos, state);

                if (otherHalfPos != null) {
                    ChestType expectedType = chestType == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;

                    if (isComplementaryChestHalf(otherHalfPos, expectedType)) {
                        String otherPosKey = otherHalfPos.getX() + "," + otherHalfPos.getY() + "," + otherHalfPos.getZ();
                        currentContainers.add(otherPosKey);
                    }
                }
            }
        }

        // Log the interaction
        if (enableLogging.get()) {
            // For double chests, check if the other half was already logged
            if (blockEntity instanceof ChestBlockEntity) {
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                if (chestType != ChestType.SINGLE) {
                    BlockPos otherHalfPos = getAdjacentChestPos(pos, state);
                    if (otherHalfPos != null) {
                        String otherPosKey = otherHalfPos.getX() + "," + otherHalfPos.getY() + "," + otherHalfPos.getZ();
                        if (loggedContainers.contains(otherPosKey)) {
                            wasAlreadyOpened = true;
                        }
                    }
                }
            }

            if (!wasAlreadyOpened || !loggedContainers.contains(posKey)) {
                loggedContainers.add(posKey);
                appendLogEntry(pos, blockEntity, wasAlreadyOpened);
            }
        }
    }

    /**
     * Appends a log entry to the JSON file immediately.
     */
    private void appendLogEntry(BlockPos pos, BlockEntity blockEntity, boolean wasAlreadyOpened) {
        try {
            File logFile = getLogFile();
            logFile.getParentFile().mkdirs();

            // Load existing data
            JsonObject root = new JsonObject();
            if (logFile.exists()) {
                try (FileReader reader = new FileReader(logFile)) {
                    JsonObject loaded = GSON.fromJson(reader, JsonObject.class);
                    if (loaded != null) {
                        root = loaded;
                    }
                }
            }

            // Get or create array for current server
            JsonArray array;
            if (root.has(currentServerKey)) {
                array = root.getAsJsonArray(currentServerKey);
            } else {
                array = new JsonArray();
            }

            // Create entry
            JsonObject entry = new JsonObject();
            entry.addProperty("x", pos.getX());
            entry.addProperty("y", pos.getY());
            entry.addProperty("z", pos.getZ());
            entry.addProperty("containerType", getContainerTypeName(blockEntity));
            entry.addProperty("timestamp", Instant.now().toString());
            entry.addProperty("wasAlreadyOpened", wasAlreadyOpened);

            // Add chest-specific data
            if (blockEntity instanceof ChestBlockEntity) {
                BlockState state = blockEntity.getCachedState();
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                entry.addProperty("chestType", chestType.name().toLowerCase());

                if (chestType != ChestType.SINGLE) {
                    Direction facing = state.get(ChestBlock.FACING);
                    entry.addProperty("facing", facing.name().toLowerCase());

                    BlockPos otherHalfPos = getAdjacentChestPos(pos, state);
                    if (otherHalfPos != null) {
                        JsonObject otherHalf = new JsonObject();
                        otherHalf.addProperty("x", otherHalfPos.getX());
                        otherHalf.addProperty("y", otherHalfPos.getY());
                        otherHalf.addProperty("z", otherHalfPos.getZ());
                        entry.add("otherHalf", otherHalf);
                    }
                }
            }

            array.add(entry);
            root.add(currentServerKey, array);

            // Save immediately
            try (FileWriter writer = new FileWriter(logFile)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            AddonTemplate.LOG.error("Failed to append HuntStorageEsp log entry", e);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        count = 0;

        // Only render if StorageESP is active
        if (!isStorageEspActive()) return;

        Set<String> currentContainers = serverOpenedContainers.getOrDefault(currentServerKey, new HashSet<>());
        boolean useShader = Modules.get().get(StorageESP.class).isShader();

        // Collect valid containers to render
        List<BlockEntity> containersToRender = new ArrayList<>();

        if (!currentContainers.isEmpty()) {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
                BlockPos pos = blockEntity.getPos();
                String posKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();

                // Check if this container was opened
                if (!currentContainers.contains(posKey)) continue;

                // CRITICAL: Verify the container still exists at this position
                if (!isTrackableContainer(blockEntity)) continue;

                containersToRender.add(blockEntity);
            }
        }

        // Always call endRender if using shader, even with empty mesh, to clear previous frame
        if (useShader) {
            mesh.begin();

            // Render all valid containers
            for (BlockEntity blockEntity : containersToRender) {
                lineColor.set(openedColor.get());
                render = openedColor.get().a > 0;

                if (render) {
                    double dist = PlayerUtils.squaredDistanceTo(
                        blockEntity.getPos().getX() + 0.5,
                        blockEntity.getPos().getY() + 0.5,
                        blockEntity.getPos().getZ() + 0.5
                    );
                    double a = 1;
                    if (dist <= fadeDistance.get() * fadeDistance.get()) {
                        a = dist / (fadeDistance.get() * fadeDistance.get());
                    }

                    lineColor.a = (int) (openedColor.get().a * a);
                    renderShader(event, blockEntity);
                    count++;
                }
            }

            // Always end the render to clear the shader state
            PostProcessShaders.STORAGE_OUTLINE.endRender(() -> MeshRenderer.begin()
                .attachments(mc.getFramebuffer())
                .clearColor(Color.CLEAR)
                .pipeline(MeteorRenderPipelines.WORLD_COLORED)
                .mesh(mesh, event.matrices)
                .end()
            );
        } else {
            // Box mode - render directly
            for (BlockEntity blockEntity : containersToRender) {
                lineColor.set(openedColor.get());
                render = openedColor.get().a > 0;

                if (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both) {
                    sideColor.set(lineColor);
                    sideColor.a = fillOpacity.get();
                }

                if (render) {
                    double dist = PlayerUtils.squaredDistanceTo(
                        blockEntity.getPos().getX() + 0.5,
                        blockEntity.getPos().getY() + 0.5,
                        blockEntity.getPos().getZ() + 0.5
                    );
                    double a = 1;
                    if (dist <= fadeDistance.get() * fadeDistance.get()) {
                        a = dist / (fadeDistance.get() * fadeDistance.get());
                    }

                    int prevLineA = lineColor.a;
                    int prevSideA = sideColor.a;

                    lineColor.a *= a;
                    sideColor.a *= a;

                    if (tracers.get() && a >= 0.075) {
                        event.renderer.line(
                            RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                            blockEntity.getPos().getX() + 0.5,
                            blockEntity.getPos().getY() + 0.5,
                            blockEntity.getPos().getZ() + 0.5,
                            lineColor
                        );
                    }

                    if (a >= 0.075) {
                        renderBox(event, blockEntity);
                    }

                    lineColor.a = prevLineA;
                    sideColor.a = prevSideA;

                    count++;
                }
            }
        }
    }

    private void renderBox(Render3DEvent event, BlockEntity blockEntity) {
        double x1 = blockEntity.getPos().getX();
        double y1 = blockEntity.getPos().getY();
        double z1 = blockEntity.getPos().getZ();

        double x2 = blockEntity.getPos().getX() + 1;
        double y2 = blockEntity.getPos().getY() + 1;
        double z2 = blockEntity.getPos().getZ() + 1;

        int excludeDir = 0;
        if (blockEntity instanceof ChestBlockEntity) {
            BlockState state = mc.world.getBlockState(blockEntity.getPos());
            if ((state.getBlock() == Blocks.CHEST || state.getBlock() == Blocks.TRAPPED_CHEST)
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                excludeDir = Dir.get(ChestBlock.getFacing(state));
            }
        }

        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            double a = 1.0 / 16.0;

            if (Dir.isNot(excludeDir, Dir.WEST)) x1 += a;
            if (Dir.isNot(excludeDir, Dir.NORTH)) z1 += a;

            if (Dir.isNot(excludeDir, Dir.EAST)) x2 -= a;
            y2 -= a * 2;
            if (Dir.isNot(excludeDir, Dir.SOUTH)) z2 -= a;
        }

        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode.get(), excludeDir);
    }

    private void renderShader(Render3DEvent event, BlockEntity blockEntity) {
        vertexConsumerProvider.setColor(lineColor);
        SimpleBlockRenderer.renderWithBlockEntity(blockEntity, event.tickDelta, vertexConsumerProvider);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
