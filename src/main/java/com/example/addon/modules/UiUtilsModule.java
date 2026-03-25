package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class UiUtilsModule extends Module {
    public UiUtilsModule() {
        super(AddonTemplate.CATEGORY, "ui-utils", "Enables Meteor UI utility overlays on GUIs.");
    }

    public static boolean isEnabled() {
        UiUtilsModule module = Modules.get().get(UiUtilsModule.class);
        return module != null && module.isActive();
    }
}
