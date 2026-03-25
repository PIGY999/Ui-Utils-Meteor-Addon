package com.example.addon;

import com.example.addon.modules.*;
import com.example.addon.commands.WaitCommand;
import com.example.addon.commands.MoveCommand;
import com.example.addon.commands.CenterCommand;
import com.example.addon.commands.CommandExample;
import com.example.addon.commands.GetClanMembersCommand;
import com.example.addon.commands.GetAllClansCommand;
import com.example.addon.commands.GetAllRanksCommand;
import com.example.addon.commands.SyncCommand;
import com.example.addon.hud.AdminVanishHud;
import com.example.addon.uiutils.OverlayContainer;
import com.example.addon.uiutils.ScreenContainer;
import com.example.addon.modules.UiUtilsModule;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.LecternScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("DiosesMC");
    public static final HudGroup HUD_GROUP = new HudGroup("DiosesMC HUD");

    private static final List<Packet<?>> DELAYED_PACKETS = new ArrayList<>();
    private boolean cancelNextSignPacket = false;
    private boolean shouldPreventPackets = false;
    private boolean shouldDelayPackets = false;
    private Screen storedScreen = null;
    private ScreenHandler storedScreenHandler = null;
    private int bypassPacketSends = 0;
    private KeyBinding restoreScreenKey;

    @Override
    public void onInitialize() {
        LOG.info("Initializing DiosesMC Addon...");

        // Registro de Módulos
        Modules.get().add(new VanishDetector());
        Modules.get().add(new LegitMiddleClick());
        Modules.get().add(new LegitChestSwap());
        Modules.get().add(new ProtectionAreaRenderer());
        Modules.get().add(new HuntStorageEsp());
        Modules.get().add(new GetAllRanksModule());
        Modules.get().add(new AutoVclip());
        Modules.get().add(new SyncClicker());
        Modules.get().add(new UiUtilsModule());




        // SUSCRIPCIÓN CRÍTICA: Esto permite que el método onTick de abajo funcione
        MeteorClient.EVENT_BUS.subscribe(this);

        // Comandos y HUD
        Commands.add(new WaitCommand());
        Commands.add(new MoveCommand());
        Commands.add(new CenterCommand());
        Commands.add(new CommandExample());
        Commands.add(new GetClanMembersCommand());
        Commands.add(new GetAllClansCommand());
        Commands.add(new GetAllRanksCommand());
        Commands.add(new SyncCommand());  // Registers .ready
        Hud.get().register(AdminVanishHud.INFO);

        restoreScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Restore Screen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "UI-Utils"));
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            if (!UiUtilsModule.isEnabled()) return;
            while (restoreScreenKey.wasPressed()) {
                if (storedScreen != null && storedScreenHandler != null && client.player != null) {
                    client.setScreen(storedScreen);
                    client.player.currentScreenHandler = storedScreenHandler;
                }
            }
        });

        OverlayContainer.hookWindow(SleepingChatScreen.class, "Sleeping Chat", (theme, window, screen) -> {
            WButton button = window.add(theme.button("Client Wake Up")).expandX().widget();
            button.action = () -> {
                mc.player.wakeUp();
                mc.setScreen(null);
            };
        });

        OverlayContainer.hookWindow(SignEditScreen.class, "Sign Edit", (theme, window, screen) -> {
            WButton button = window.add(theme.button("Clientside Close")).expandX().widget();
            button.action = () -> {
                mc.setScreen(null);
                cancelNextSignPacket = true;
            };
        });

        List<Pair<Class<? extends Screen>, String>> screens = List.of(
            new ObjectObjectImmutablePair<>(HandledScreen.class, "UI-Utils"),
            new ObjectObjectImmutablePair<>(LecternScreen.class, "UI-Utils")
        );

        OverlayContainer.hookWindows(screens, (theme, window, screen) -> {
            ScreenContainer screenContainer = null;
            if (screen instanceof com.example.addon.uiutils.IScreen iscreen) {
                screenContainer = iscreen.meteor_ui_utils$getScreenContainer();
            }

            WButton clientSideClose = window.add(theme.button("Close without packet")).expandX().widget();
            clientSideClose.action = () -> mc.setScreen(null);

            WButton serverSideClose = window.add(theme.button("De-sync")).expandX().widget();
            serverSideClose.action = () -> {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            };

            WButton sendPackets = window.add(theme.button("Send packets: " + (!shouldPreventPackets))).expandX().widget();
            sendPackets.action = () -> {
                shouldPreventPackets = !shouldPreventPackets;
                sendPackets.set("Send packets: " + (!shouldPreventPackets));
            };

            WButton delayPackets = window.add(theme.button("Delay packets: " + shouldDelayPackets)).expandX().widget();
            delayPackets.action = () -> {
                boolean wasDelaying = shouldDelayPackets;
                shouldDelayPackets = !shouldDelayPackets;
                delayPackets.set("Delay packets: " + shouldDelayPackets);

                if (wasDelaying && !shouldDelayPackets && !DELAYED_PACKETS.isEmpty()) {
                    for (var packet : DELAYED_PACKETS) {
                        mc.getNetworkHandler().sendPacket(packet);
                    }

                    DELAYED_PACKETS.clear();
                }
            };

            WButton disconnect = window.add(theme.button("Disconnect and send packets")).expandX().widget();
            disconnect.action = () -> {
                if (!DELAYED_PACKETS.isEmpty()) {
                    shouldDelayPackets = false;

                    for (var packet : DELAYED_PACKETS) mc.getNetworkHandler().sendPacket(packet);
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal("Disconnected [%s packets sent]".formatted(DELAYED_PACKETS.size())));
                    DELAYED_PACKETS.clear();
                }
            };

            WButton save = window.add(theme.button("Save GUI")).expandX().widget();
            save.action = () -> {
                storedScreen = screen;
                storedScreenHandler = mc.player.currentScreenHandler;
            };

            window.add(theme.label("Sync ID: " + mc.player.currentScreenHandler.syncId));
            window.add(theme.label("Revision: " + mc.player.currentScreenHandler.getRevision()));

            WButton copySyncId = window.add(theme.button("Copy Sync ID")).expandX().widget();
            copySyncId.action = () -> {
                mc.keyboard.setClipboard(String.valueOf(mc.player.currentScreenHandler.syncId));
            };

            WButton copyRevision = window.add(theme.button("Copy Revision")).expandX().widget();
            copyRevision.action = () -> {
                mc.keyboard.setClipboard(String.valueOf(mc.player.currentScreenHandler.getRevision()));
            };

            WButton openFabricate = window.add(theme.button("Fabricate packet")).expandX().widget();

            final WWindow clickSlotWindow;
            final WWindow buttonClickWindow;
            final WWindow fabricateWindow;
            if (screenContainer != null) {
                clickSlotWindow = screenContainer.add(theme.window("Click Slot Packet")).widget();
                clickSlotWindow.minWidth = 260;
                clickSlotWindow.visible = false;

                buttonClickWindow = screenContainer.add(theme.window("Button Click Packet")).widget();
                buttonClickWindow.minWidth = 260;
                buttonClickWindow.visible = false;

                fabricateWindow = screenContainer.add(theme.window("Fabricate packet")).widget();
                fabricateWindow.minWidth = 200;
                fabricateWindow.visible = false;
            } else {
                clickSlotWindow = null;
                buttonClickWindow = null;
                fabricateWindow = null;
            }

            if (fabricateWindow != null) {
                WButton openClickSlot = fabricateWindow.add(theme.button("Click Slot")).expandX().widget();
                WButton openButtonClick = fabricateWindow.add(theme.button("Button Click")).expandX().widget();

                openClickSlot.action = () -> {
                    if (clickSlotWindow != null) clickSlotWindow.visible = !clickSlotWindow.visible;
                };
                openButtonClick.action = () -> {
                    if (buttonClickWindow != null) buttonClickWindow.visible = !buttonClickWindow.visible;
                };
            }

            if (clickSlotWindow != null) {
                var clickSection = clickSlotWindow.add(theme.section("Click Slot Packet", true)).expandX().widget();
                var clickTable = clickSection.add(theme.table()).expandX().widget();

                clickTable.add(theme.label("Sync Id"));
                WIntEdit syncIdEdit = clickTable.add(theme.intEdit(mc.player.currentScreenHandler.syncId, -1, 9999, true)).expandX().widget();
                clickTable.row();

                clickTable.add(theme.label("Revision"));
                WIntEdit revisionEdit = clickTable.add(theme.intEdit(mc.player.currentScreenHandler.getRevision(), -1, 9999, true)).expandX().widget();
                clickTable.row();

                clickTable.add(theme.label("Slot"));
                WIntEdit slotEdit = clickTable.add(theme.intEdit(0, -1, 9999, true)).expandX().widget();
                clickTable.row();

                clickTable.add(theme.label("Button"));
                WIntEdit buttonEdit = clickTable.add(theme.intEdit(0, -1, 40, true)).expandX().widget();
                clickTable.row();

                clickTable.add(theme.label("Action"));
                WDropdown<SlotActionType> actionType = clickTable.add(theme.dropdown(SlotActionType.PICKUP)).expandX().widget();
                clickTable.row();

                clickTable.add(theme.label("Send"));
                WButton sendClickSlot = clickTable.add(theme.button("Send")).widget();
                clickTable.row();

                clickTable.add(theme.label("Delay"));
                WCheckbox clickDelay = clickTable.add(theme.checkbox(false)).widget();
                clickTable.row();

                clickTable.add(theme.label("Times to send"));
                WIntEdit clickTimes = clickTable.add(theme.intEdit(1, 1, 1000, true)).expandX().widget();

                sendClickSlot.action = () -> {
                    if (mc.player == null || mc.getNetworkHandler() == null) return;
                    SlotActionType action = actionType.get();
                    if (action == null) return;

                    ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
                        syncIdEdit.get(),
                        revisionEdit.get(),
                        (short) slotEdit.get(),
                        (byte) buttonEdit.get(),
                        action,
                        new Int2ObjectArrayMap<>(),
                        ItemStackHash.EMPTY
                    );

                    for (int i = 0; i < clickTimes.get(); i++) {
                        sendFabricatedPacket(packet, clickDelay.checked);
                    }
                };
            }

            if (buttonClickWindow != null) {
                var buttonSection = buttonClickWindow.add(theme.section("Button Click Packet", true)).expandX().widget();
                var buttonTable = buttonSection.add(theme.table()).expandX().widget();

                buttonTable.add(theme.label("Sync Id"));
                WIntEdit buttonSyncIdEdit = buttonTable.add(theme.intEdit(mc.player.currentScreenHandler.syncId, -1, 9999, true)).expandX().widget();
                buttonTable.row();

                buttonTable.add(theme.label("Button Id"));
                WIntEdit buttonIdEdit = buttonTable.add(theme.intEdit(0, -1, 9999, true)).expandX().widget();
                buttonTable.row();

                buttonTable.add(theme.label("Send"));
                WButton sendButtonClick = buttonTable.add(theme.button("Send")).widget();
                buttonTable.row();

                buttonTable.add(theme.label("Delay"));
                WCheckbox buttonDelay = buttonTable.add(theme.checkbox(false)).widget();
                buttonTable.row();

                buttonTable.add(theme.label("Times to send"));
                WIntEdit buttonTimes = buttonTable.add(theme.intEdit(1, 1, 1000, true)).expandX().widget();

                sendButtonClick.action = () -> {
                    if (mc.player == null || mc.getNetworkHandler() == null) return;
                    ButtonClickC2SPacket packet = new ButtonClickC2SPacket(buttonSyncIdEdit.get(), buttonIdEdit.get());
                    for (int i = 0; i < buttonTimes.get(); i++) {
                        sendFabricatedPacket(packet, buttonDelay.checked);
                    }
                };
            }

            if (fabricateWindow != null) {
                openFabricate.action = () -> fabricateWindow.visible = !fabricateWindow.visible;
            }
        });

    }

    private void sendFabricatedPacket(Packet<?> packet, boolean delay) {
        if (mc.getNetworkHandler() == null) return;

        if (delay && shouldDelayPackets) {
            DELAYED_PACKETS.add(packet);
            return;
        }

        bypassPacketSends++;
        try {
            mc.getNetworkHandler().sendPacket(packet);
        } finally {
            bypassPacketSends--;
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send send) {
        if (bypassPacketSends > 0) return;
        if (cancelNextSignPacket && send.packet instanceof UpdateSignC2SPacket) {
            cancelNextSignPacket = false;
            send.cancel();
        } else if (shouldPreventPackets && (send.packet instanceof ClickSlotC2SPacket || send.packet instanceof ButtonClickC2SPacket)) send.cancel();
        else if (shouldDelayPackets && (send.packet instanceof ClickSlotC2SPacket || send.packet instanceof ButtonClickC2SPacket)) {
            DELAYED_PACKETS.add(send.packet);
            send.cancel();
        }
    }



    // --- Utilidades del Addon ---

    public static final Text PREFIX = Text.empty()
        .append(Text.literal("[").formatted(Formatting.WHITE))
        .append(Text.literal("DiosesMC Addon").formatted(Formatting.AQUA))
        .append(Text.literal("] ").formatted(Formatting.WHITE));

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "DiosesMC Addon"), key), Utils.getFileWorldName()), filename);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
