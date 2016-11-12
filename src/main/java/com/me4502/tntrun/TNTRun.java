package com.me4502.tntrun;

import com.google.inject.Inject;
import com.me4502.tntrun.command.EndCommand;
import com.me4502.tntrun.command.JoinCommand;
import com.me4502.tntrun.command.LeaveCommand;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Function;

@Plugin(
        id = "tntrun",
        name = "TNTRun",
        description = "TNTRun plugin",
        authors = {
                "Me4502"
        }
)
public class TNTRun {

    @Inject
    public PluginContainer container;

    @Inject
    @DefaultConfig(sharedRoot = false)
    public Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = false)
    public ConfigurationLoader<CommentedConfigurationNode> configManager;

    public TNTRunConfiguration config;

    public GameManager gameManager;

    private ConfigurationNode messagesNode;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {

        if (!new File(defaultConfig.toFile().getParentFile(), "messages.conf").exists()) {
            URL jarConfigFile = this.getClass().getResource("messages.conf");
            ConfigurationLoader<CommentedConfigurationNode> loader = HoconConfigurationLoader.builder().setURL(jarConfigFile).build();
            ConfigurationLoader<CommentedConfigurationNode> fileLoader = HoconConfigurationLoader.builder().setFile(new File(defaultConfig.toFile().getParentFile(), "messages.conf")).build();
            try {
                fileLoader.save(loader.load());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ConfigurationLoader<CommentedConfigurationNode> fileLoader = HoconConfigurationLoader.builder().setFile(new File(defaultConfig.toFile().getParentFile(), "messages.conf")).build();
        try {
            messagesNode = fileLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        config = new TNTRunConfiguration(configManager);
        config.load();

        CommandSpec joinCommand = CommandSpec.builder()
                .description(Text.of("Join a game"))
                .executor(new JoinCommand(this))
                .build();

        CommandSpec leaveCommand = CommandSpec.builder()
                .description(Text.of("Leave a game"))
                .executor(new LeaveCommand(this))
                .build();

        CommandSpec endCommand = CommandSpec.builder()
                .description(Text.of("End a game"))
                .permission("tntrun.endgame")
                .executor(new EndCommand(this))
                .build();

        CommandSpec tntRunCommand = CommandSpec.builder()
                .description(Text.of("TNT Run base command"))
                .child(joinCommand, "join")
                .child(leaveCommand, "leave", "quit")
                .child(endCommand, "end")
                .build();

        Sponge.getCommandManager().register(this, tntRunCommand, "tntrun", "tnt");

        gameManager = new GameManager(this);
        Sponge.getEventManager().registerListeners(this, gameManager);
    }

    public Text getMessage(String message) {
        return TextSerializers.FORMATTING_CODE.deserialize(messagesNode.getNode(message.replace(".", "_")).getString("Unknown message"));
    }

    public Text getMessage(String message, Function<String, String> replacer) {
        String replaced = replacer.apply(messagesNode.getNode(message.replace(".", "_")).getString("Unknown message: " + message.replace(".", "_")));
        return TextSerializers.FORMATTING_CODE.deserialize(replaced);
    }
}
