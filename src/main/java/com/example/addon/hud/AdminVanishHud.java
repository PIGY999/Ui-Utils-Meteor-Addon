package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.VanishDetector;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class AdminVanishHud extends HudElement {
    public static final HudElementInfo<AdminVanishHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "admin-vanish",
        "Shows admins that are online/vanish.",
        AdminVanishHud::new
    );

    private final Color textColor = new Color(255, 255, 255);

    public AdminVanishHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MinecraftClient.getInstance().player == null) return;

        VanishDetector detector = Modules.get().get(VanishDetector.class);
        List<String> visible = detector != null ? detector.getVisibleDisplayNames() : List.of();
        List<String> vanished = detector != null ? detector.getVanishedDisplayNames() : List.of();
        List<String> hotAdmins = detector != null ? detector.getHotAdminsDisplayNames() : List.of();
        List<String> recentAdmins = detector != null ? detector.getRecentAdminsDisplayNames() : List.of();

        List<String> lines = new ArrayList<>();
        lines.add("Admins");
        lines.add("Visible: " + (visible.isEmpty() ? "None" : String.join(", ", visible)));
        lines.add("Vanish: " + (vanished.isEmpty() ? "None" : String.join(", ", vanished)));
        lines.add("Hot ban: " + (hotAdmins.isEmpty() ? "None" : String.join(", ", hotAdmins)));
        lines.add("Recent: " + (recentAdmins.isEmpty() ? "None" : String.join(", ", recentAdmins)));

        double x = getX();
        double y = getY();
        double lineHeight = renderer.textHeight();
        double width = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            renderer.text(line, x, y + (i * lineHeight), textColor, true);
            width = Math.max(width, renderer.textWidth(line));
        }

        setSize(width, lineHeight * lines.size());
    }
}
