package com.example.addon.mixin;

import com.example.addon.uiutils.IScreen;
import com.example.addon.uiutils.OverlayContainer;
import com.example.addon.uiutils.ScreenContainer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen implements IScreen {
    @Unique private ScreenContainer container;

    private AbstractSignEditScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>(Lnet/minecraft/block/entity/SignBlockEntity;ZZ)V", at = @At("TAIL"))
    private void injectConstructor(SignBlockEntity blockEntity, boolean front, boolean filtered, CallbackInfo ci) {
        this.container = new OverlayContainer<>(this);
        meteor_ui_utils$setScreenContainer(this.container);
    }

    @Inject(method = "<init>(Lnet/minecraft/block/entity/SignBlockEntity;ZZLnet/minecraft/text/Text;)V", at = @At("TAIL"))
    private void injectConstructor(SignBlockEntity blockEntity, boolean front, boolean filtered, Text title, CallbackInfo ci) {
        this.container = new OverlayContainer<>(this);
        meteor_ui_utils$setScreenContainer(this.container);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void injectInit(CallbackInfo ci) {
        if (container != null) container.init();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (container == null) return;
        container.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void injectKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (container == null) return;
        if (container.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void injectCharTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (container == null) return;
        if (container.charTyped(chr, modifiers)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
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
