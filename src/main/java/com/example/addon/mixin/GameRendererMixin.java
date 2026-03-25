package com.example.addon.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.addon.uiutils.IScreen;
import com.example.addon.uiutils.ScreenContainer;
import com.example.addon.modules.UiUtilsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private FogRenderer fogRenderer;
    @Shadow @Final private GuiRenderer guiRenderer;
    @Shadow @Final private GuiRenderState guiState;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void onRenderGui(RenderTickCounter tickCounter, boolean tick, CallbackInfo info) {
        if (!UiUtilsModule.isEnabled()) return;
        if (mc.currentScreen instanceof IScreen screen) {
            ScreenContainer widgetScreen = screen.meteor_ui_utils$getScreenContainer();
            if (widgetScreen != null) {
                guiState.clear();

                widgetScreen.renderCustom(
                    new DrawContext(mc, guiState),
                    (int) mc.mouse.getScaledX(mc.getWindow()),
                    (int) mc.mouse.getScaledY(mc.getWindow()),
                    tickCounter.getDynamicDeltaTicks()
                );

                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mc.getFramebuffer().getDepthAttachment(), 1.0);
                guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
                guiRenderer.incrementFrame();
            }
        }
    }
}
