package com.example.addon;

import com.example.addon.modules.*;
import com.example.addon.commands.WaitCommand;
import com.example.addon.commands.MoveCommand;
import com.example.addon.commands.CenterCommand;
import com.example.addon.commands.CommandExample;
import com.example.addon.commands.GetClanMembersCommand;
import com.example.addon.commands.GetAllClansCommand;
import com.example.addon.commands.GetAllRanksCommand;
import com.example.addon.hud.AdminVanishHud;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.world.TickEvent; // El tick real de Meteor
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.io.File;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("DiosesMC");
    public static final HudGroup HUD_GROUP = new HudGroup("DiosesMC HUD");

    @Override
    public void onInitialize() {
        LOG.info("Initializing DiosesMC Addon...");

        // Registro de Módulos
        Modules.get().add(new VanishDetector());
        Modules.get().add(new LegitMiddleClick());
        Modules.get().add(new LegitChestSwap());
        Modules.get().add(new ProtectionAreaRenderer());
        Modules.get().add(new HuntStorageEsp());




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
        Hud.get().register(AdminVanishHud.INFO);

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
