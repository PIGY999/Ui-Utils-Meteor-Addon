package com.example.addon.commands;

import com.example.addon.utils.ChatPacketSender;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A prefix command that waits for a specified amount of time before executing another command.
 * Usage: .wait <milliseconds> <command>
 * 
 * Examples:
 *   .wait 5000 /home           -> Sends "/home" to server after 5 seconds (works with chat disabled)
 *   .wait 3000 .drop stone    -> Executes Meteor's ".drop" command after 3 seconds (needs chat enabled)
 *   .wait 1000 msg hi         -> Sends "/msg hi" to server after 1 second (works with chat disabled)
 * 
 * Note: Server commands work with chat disabled. Meteor commands (starting with .) require chat enabled.
 */
public class WaitCommand extends Command {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WaitCommand() {
        super("wait", "Waits for a specified time before executing a command.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("milliseconds", IntegerArgumentType.integer(1))
            .then(argument("command", StringArgumentType.greedyString())
                .executes(context -> {
                    int waitTime = IntegerArgumentType.getInteger(context, "milliseconds");
                    String command = StringArgumentType.getString(context, "command");
                    executeAfterDelay(waitTime, command);
                    info("Waiting " + waitTime + "ms before executing: " + command);
                    return SINGLE_SUCCESS;
                })
            )
        );
    }

    private void executeAfterDelay(int milliseconds, String command) {
        scheduler.schedule(() -> {
            mc.execute(() -> {
                if (command.startsWith(".")) {
                    // Meteor prefix command - send through chat system
                    // This requires chat to be enabled since Meteor intercepts chat
                    ChatUtils.sendPlayerMsg(command);
                } else if (command.startsWith("/")) {
                    // Explicit slash command - send via packet (works with chat disabled)
                    ChatPacketSender.sendCommand(command.substring(1));
                } else {
                    // No prefix - assume it's a server command, send via packet (works with chat disabled)
                    ChatPacketSender.sendCommand(command);
                }
            });
        }, milliseconds, TimeUnit.MILLISECONDS);
    }
}
