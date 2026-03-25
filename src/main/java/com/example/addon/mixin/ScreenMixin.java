package com.example.addon.mixin;

import com.example.addon.uiutils.IScreen;
import com.example.addon.uiutils.ScreenContainer;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Screen.class)
public abstract class ScreenMixin implements IScreen {
    @Unique private ScreenContainer container;

    @Override
    public void meteor_ui_utils$setScreenContainer(ScreenContainer container) {
        this.container = container;
    }

    @Override
    public ScreenContainer meteor_ui_utils$getScreenContainer() {
        return container;
    }
}
