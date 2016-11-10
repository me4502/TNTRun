package com.me4502.tntrun.command;

import com.me4502.tntrun.TNTRun;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;

public class LeaveCommand implements CommandExecutor {

    private TNTRun plugin;

    public LeaveCommand(TNTRun plugin) {
        this.plugin = plugin;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {

        if (src instanceof Player) {
            if (plugin.gameManager.isPlayer((Player) src)) {
                plugin.gameManager.removePlayer((Player) src);
            } else {
                src.sendMessage(plugin.getMessage("error.notingame"));
            }
        } else {
            src.sendMessage(plugin.getMessage("error.notaplayer"));
        }

        return CommandResult.success();
    }
}
