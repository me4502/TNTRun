package com.me4502.tntrun;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TNTRunConfiguration {

    private ConfigurationLoader<CommentedConfigurationNode> configurationLoader;

    public int minPlayers;
    public int maxPlayers;
    public int minHeight;
    public int preGameTime;

    public int entryFee;

    public Location<World> minArena;
    public Location<World> maxArena;

    public Location<World> lobbySpawn;

    public String schematicName;
    public Location<World> schematicOrigin;

    public List<String> rewardCommands = new ArrayList<>();

    public List<Location<World>> spawnPositions = new ArrayList<>();

    public TNTRunConfiguration(ConfigurationLoader<CommentedConfigurationNode> configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    public void load() {
        try {
            ConfigurationOptions options = ConfigurationOptions.defaults();
            options.setShouldCopyDefaults(true);
            ConfigurationNode node = configurationLoader.load(options);

            minPlayers = node.getNode("minimum-players").getInt(4);
            maxPlayers = node.getNode("maximum-players").getInt(8);

            entryFee = node.getNode("entry-fee").getInt(5);

            minHeight = node.getNode("minimum-height").getInt(10);
            preGameTime = node.getNode("pre-game-time").getInt(30);

            rewardCommands.addAll(node.getNode("reward-commands").getList(TypeToken.of(String.class), Lists.newArrayList("give @p minecraft:stone")));

            minArena = getLocation(node.getNode("min-arena").getString("world,10,64,10"));
            maxArena = getLocation(node.getNode("max-arena").getString("world,30,128,30"));

            lobbySpawn = getLocation(node.getNode("lobby-spawn").getString("world,0,10,0"));
            schematicOrigin = getLocation(node.getNode("schematic-origin").getString("world,0,0,0"));

            schematicName = node.getNode("schematic").getString("arena");

            spawnPositions.addAll(node.getNode("spawn-positions").getList(TypeToken.of(String.class), Lists.newArrayList("world,12,65,12", "world,34,234,1"))
                    .stream().map(this::getLocation).collect(Collectors.toList()));

            if (spawnPositions.size() < maxPlayers) {
                System.out.println("Warning: Less spawn positions than max players!");
            }

            configurationLoader.save(node);
        } catch (IOException | ObjectMappingException e) {
            e.printStackTrace();
        }
    }

    private Location<World> getLocation(String location) {
        String[] bits = location.split(",");
        return new Location<>(Sponge.getServer().getWorld(bits[0]).get(), Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), Integer.parseInt(bits[3]));
    }
}
