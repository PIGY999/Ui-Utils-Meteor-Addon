package com.example.addon.uiutils;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class OverlayContainer<T extends Screen> extends ScreenContainer {
    private static final Map<Class<? extends Screen>, List<Pair<String, InitWidgets>>> OVERLAYS = new Object2ObjectOpenHashMap<>();

    private final T parentScreen;

    public OverlayContainer(T parentScreen) {
        super(GuiThemes.get());

        this.parentScreen = parentScreen;
    }

    @Override
    public void initWidgets() {
        for (var entry : OVERLAYS.entrySet()) {
            if (entry.getKey().isAssignableFrom(parentScreen.getClass())) {
                for (var overlay : entry.getValue()) {
                    WWindow window = add(theme.window(overlay.left())).widget();
                    overlay.right().accept(theme, window, parentScreen);
                }
            }
        }
    }

    public static void hookWindow(Class<? extends Screen> screenClass, String wWindowName, InitWidgets initWidgets) {
        List<Pair<String, InitWidgets>> list = OVERLAYS.computeIfAbsent(screenClass, k -> new ObjectArrayList<>());
        list.add(new ObjectObjectImmutablePair<>(wWindowName, initWidgets));
    }

    public static void hookWindows(List<Pair<Class<? extends Screen>, String>> screens, InitWidgets initWidgets) {
        for (var screenEntry : screens) {
            hookWindow(screenEntry.left(), screenEntry.right(), initWidgets);
        }
    }

    @FunctionalInterface
    public interface InitWidgets {
        void accept(GuiTheme theme, WWindow window, Screen screen);
    }
}
