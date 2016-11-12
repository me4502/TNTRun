package com.me4502.tntrun.command;

import com.me4502.tntrun.TNTRun;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;

public class EndCommand implements CommandExecutor {

    private TNTRun plugin;

    public EndCommand(TNTRun plugin) {
        this.plugin = plugin;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        plugin.gameManager.endGame();
        return CommandResult.success();
    }
}
