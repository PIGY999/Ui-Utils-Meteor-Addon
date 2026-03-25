package com.example.addon.uiutils;

import org.spongepowered.asm.mixin.Unique;

public interface IScreen {
    @Unique
    void meteor_ui_utils$setScreenContainer(ScreenContainer container);

    ScreenContainer meteor_ui_utils$getScreenContainer();
}
