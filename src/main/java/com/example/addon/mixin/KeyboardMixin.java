package com.example.addon.mixin;

import com.example.addon.modules.UiUtilsModule;
import com.example.addon.uiutils.IScreen;
import com.example.addon.uiutils.ScreenContainer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.*;

@Environment(EnvType.CLIENT)
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void injectOnKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!UiUtilsModule.isEnabled()) return;

        Screen screen = client.currentScreen;
        if (!(screen instanceof IScreen iScreen)) return;

        ScreenContainer container = iScreen.meteor_ui_utils$getScreenContainer();
        if (container == null) return;

        boolean handled = false;
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            handled = container.keyPressed(key, scancode, modifiers);
        }
        else if (action == GLFW_RELEASE) {
            handled = container.keyReleased(key, scancode, modifiers);
        }

        if (handled) ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void injectOnChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (!UiUtilsModule.isEnabled()) return;

        Screen screen = client.currentScreen;
        if (!(screen instanceof IScreen iScreen)) return;

        ScreenContainer container = iScreen.meteor_ui_utils$getScreenContainer();
        if (container == null) return;

        boolean handled = false;
        if (Character.isBmpCodePoint(codePoint)) {
            handled = container.charTyped((char) codePoint, modifiers);
        }
        else if (Character.isValidCodePoint(codePoint)) {
            handled = container.charTyped(Character.highSurrogate(codePoint), modifiers);
            handled |= container.charTyped(Character.lowSurrogate(codePoint), modifiers);
        }

        if (handled) ci.cancel();
    }
}
