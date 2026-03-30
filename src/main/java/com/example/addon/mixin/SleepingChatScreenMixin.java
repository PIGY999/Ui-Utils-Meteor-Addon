package com.example.addon.mixin;

import com.example.addon.uiutils.IScreen;
import com.example.addon.uiutils.OverlayContainer;
import com.example.addon.uiutils.ScreenContainer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SleepingChatScreen.class)
public abstract class SleepingChatScreenMixin extends ChatScreen implements IScreen {
    @Unique private ScreenContainer container;
    private SleepingChatScreenMixin(String originalChatText) {
        super(originalChatText);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectConstructor(CallbackInfo ci) {
        this.container = new OverlayContainer<>(this);
        meteor_ui_utils$setScreenContainer(this.container);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void injectInit(CallbackInfo ci) {
        if (container != null) container.init();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (container == null) return false;
        if (container.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (container == null) return false;
        if (container.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (container == null) return;
        container.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (container == null) return false;
        if (container.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (container == null) return false;
        if (container.keyReleased(keyCode, scanCode, modifiers)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (container == null) return false;
        if (container.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (container == null) return false;
        if (container.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (container == null) return;
        container.resize(client, width, height);
        super.resize(client, width, height);
    }

    @Override
    public void removed() {
        if (container == null) return;
        container.removed();
        super.removed();
    }
}
