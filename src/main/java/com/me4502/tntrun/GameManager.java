package com.me4502.tntrun;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.sponge.SpongeWorldEdit;
import com.sk89q.worldedit.world.DataException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameManager {

    private TNTRun plugin;

    private GameState gameState;

    private Task countingTask;
    private AtomicInteger countingInterval = new AtomicInteger(0);

    private Map<UUID, Location<World>> players = new HashMap<>();
    private Set<UUID> priorPlayers = new HashSet<>();

    GameManager(TNTRun plugin) {
        this.plugin = plugin;

        initialize();
    }

    private void initialize() {
        gameState = GameState.WAITING;

        players.clear();
        priorPlayers.clear();

        countingInterval.set(0);
    }

    private void startGame() {
        gameState = GameState.PLAYING;
    }

    public boolean canPlayerJoin(Player player) {
        if (gameState != GameState.COUNTING && gameState != GameState.WAITING) {
            player.sendMessage(plugin.getMessage("error.gameinprogress"));
            return false;
        }

        if (players.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("error.alreadyingame"));
            return false;
        }

        if (player.getInventory().size() > 0) {
            player.sendMessage(plugin.getMessage("error.inventoryfull"));
            return false;
        }

        if (plugin.config.entryFee > 0 && Sponge.getServiceManager().isRegistered(EconomyService.class)) {
            EconomyService economyService = Sponge.getServiceManager().getRegistration(EconomyService.class).get().getProvider();
            UniqueAccount account = economyService.getOrCreateAccount(player.getUniqueId()).get();
            if (account.getBalance(economyService.getDefaultCurrency()).intValue() < plugin.config.entryFee) {
                player.sendMessage(plugin.getMessage("error.notenoughmoney"));
                return false;
            }
        }

        return true;
    }

    private void movePlayers() {
        for (int i = 0; i < getLivingPlayers().size(); i++) {
            Player player = getLivingPlayers().get(i);
            player.setLocation(plugin.config.spawnPositions.get(i % plugin.config.spawnPositions.size()));
        }
    }

    public void addPlayer(Player player) {
        players.put(player.getUniqueId(), player.getLocation().copy());
        priorPlayers.add(player.getUniqueId());

        player.setLocation(plugin.config.lobbySpawn);
        player.offer(Keys.GAME_MODE, GameModes.SURVIVAL);
        player.offer(Keys.IS_FLYING, false);
        player.offer(Keys.CAN_FLY, false);

        if (players.size() == 1) {
            Sponge.getServer().getBroadcastChannel().send(plugin.getMessage("message.gamequeued"));
        }

        if (plugin.config.entryFee > 0 && Sponge.getServiceManager().isRegistered(EconomyService.class)) {
            EconomyService economyService = Sponge.getServiceManager().getRegistration(EconomyService.class).get().getProvider();
            UniqueAccount account = economyService.getOrCreateAccount(player.getUniqueId()).get();

            account.withdraw(economyService.getDefaultCurrency(), BigDecimal.valueOf(plugin.config.entryFee), Cause.source(plugin.container).build());
        }

        if (gameState == GameState.WAITING) {
            if (players.size() < plugin.config.minPlayers) {
                sendToAll(plugin.getMessage("message.playerstostart", s -> s.replace("%1", String.valueOf(players.size())).replace("%2", String.valueOf(plugin.config.minPlayers))));
            } else {
                sendToAll(plugin.getMessage("message.startinginseconds", s -> s.replace("%1", String.valueOf(30))));
                gameState = GameState.COUNTING;

                countingInterval.set(0);
                countingTask = Sponge.getScheduler().createTaskBuilder().interval(1, TimeUnit.SECONDS).execute(task -> {
                    countingInterval.set(countingInterval.get() + 1);
                    if (gameState == GameState.COUNTING) {
                        if (countingInterval.get() % 5 == 0) {
                            if (plugin.config.preGameTime - countingInterval.get() == 0) {
                                sendToAll(plugin.getMessage("message.startinggame"));
                                countingInterval.set(0);
                                movePlayers();
                                gameState = GameState.STARTING;
                            } else {
                                sendToAll(plugin.getMessage("message.startinginseconds", s -> s.replace("%1", String.valueOf((plugin.config.preGameTime - countingInterval.get())))));
                            }
                        }
                    } else if (gameState == GameState.STARTING) {
                        if (countingInterval.get() == 5) {
                            startGame();
                            countingTask.cancel();
                        }
                    }
                }).submit(plugin);
            }
        }
    }

    public boolean isPlayer(Player player) {
        return players.containsKey(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        players.remove(player.getUniqueId());
        priorPlayers.remove(player.getUniqueId());

        if (gameState == GameState.WAITING) {
            if (players.size() < plugin.config.minPlayers) {
                sendToAll(plugin.getMessage("message.playerstostart", s -> s.replace("%1", String.valueOf(players.size())).replace("%2", String.valueOf(plugin.config.minPlayers))));
            } else if (gameState == GameState.COUNTING) {
                sendToAll(plugin.getMessage("error.notenoughplayers"));
                gameState = GameState.WAITING;
                if (countingTask != null) {
                    countingTask.cancel();
                }
            }
        }
    }

    @Listener
    public void onPlayerLeave(ClientConnectionEvent.Disconnect event) {
        if (players.containsKey(event.getTargetEntity().getUniqueId())) {
            removePlayer(event.getTargetEntity());
        }
    }

    @Listener
    public void onPlayerMove(MoveEntityEvent event, @Getter("getTargetEntity") Player player) {
        if (players.containsKey(player.getUniqueId())) {
            if (gameState == GameState.PLAYING) {
                for (Location<World> block : getRemovableBlocks(player)) {
                    if (block.getBlockType() == BlockTypes.TNT || block.getRelative(Direction.DOWN).getBlockType() == BlockTypes.TNT) {
                        Sponge.getScheduler().createTaskBuilder().delayTicks(5).execute(task -> block.setBlockType(BlockTypes.AIR, Cause.source(plugin.container).build())).submit(plugin);
                    }
                }

                if (player.getLocation().getY() <= plugin.config.minHeight) {
                    killPlayer(player);
                }
            } else if (gameState == GameState.STARTING) {
                if (!(event instanceof MoveEntityEvent.Teleport) && event.getFromTransform().getPosition().distanceSquared(event.getToTransform().getPosition()) < 5*5)
                    event.setToTransform(event.getToTransform().setPosition(event.getFromTransform().getPosition()));
            }
        }
    }

    private Set<Location<World>> getRemovableBlocks(Player player) {
        Set<Location<World>> removableBlocks = new HashSet<>();

        Location<World> playerLocation = player.getLocation();

        for (double ox = -0.2; ox <= 0.2; ox += 0.2) {
            for (double oz = -0.2; oz <= 0.2; oz += 0.2) {
                Location<World> main = playerLocation.add(ox, 0, oz).getRelative(Direction.DOWN);
                removableBlocks.add(main);
                removableBlocks.add(main.getRelative(Direction.DOWN));
            }
        }

        return removableBlocks;
    }

    private List<Player> getLivingPlayers() {
        return players.keySet().stream().map((uuid -> Sponge.getServer().getPlayer(uuid))).filter((Optional::isPresent)).map((Optional::get)).collect(Collectors.toList());
    }

    private void sendToAll(Text text) {
        getLivingPlayers().forEach((player -> player.sendMessage(text)));
    }

    private void killPlayer(Player player) {
        Location<World> location = players.remove(player.getUniqueId());
        player.setLocation(location);

        List<Player> livingPlayers = getLivingPlayers();

        sendToAll(plugin.getMessage("message.death", s -> s.replace("%1", player.getName()).replace("%2", String.valueOf(livingPlayers.size()))));

        if (livingPlayers.size() <= 1) {
            livingPlayers.stream().findFirst().ifPresent((this::playerWins));

            endGame();
        }
    }

    private void playerWins(Player player) {
        priorPlayers.stream().map(uuid -> Sponge.getServer().getPlayer(uuid)).filter((Optional::isPresent)).map((Optional::get)).forEach(player1 -> player1.sendMessage(plugin.getMessage("message.winner", s -> s.replace("%1", player.getName()))));

        for (String command : plugin.config.rewardCommands) {
            Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command.replace("@p", player.getName()));
        }
    }

    private void endGame() {
        gameState = GameState.GAMEOVER;

        Sponge.getScheduler().createTaskBuilder().delay(5, TimeUnit.SECONDS).execute(task -> {
            for (Map.Entry<UUID, Location<World>> playerEntry : players.entrySet()) {
                Player thisPlayer = Sponge.getServer().getPlayer(playerEntry.getKey()).orElse(null);
                if (thisPlayer != null) {
                    thisPlayer.sendMessage(plugin.getMessage("message.endgame"));
                    thisPlayer.setLocation(playerEntry.getValue());
                }
            }

            EditSession es = WorldEdit.getInstance().getEditSessionFactory().getEditSession(SpongeWorldEdit.inst().getWorld(plugin.config.schematicOrigin.getExtent()), -1);
            try {
                CuboidClipboard cc = CuboidClipboard.loadSchematic(new File(plugin.defaultConfig.toFile().getParentFile(), plugin.config.schematicName));
                int x = plugin.config.schematicOrigin.getBlockX();
                int y = plugin.config.schematicOrigin.getBlockY();
                int z = plugin.config.schematicOrigin.getBlockZ();
                cc.paste(es, new Vector(x, y, z), false);
            } catch (DataException | MaxChangedBlocksException | IOException e) {
                e.printStackTrace();
            }

            initialize();
        }).submit(plugin);
    }

    private enum GameState {
        WAITING,
        COUNTING,
        STARTING,
        PLAYING,
        GAMEOVER
    }
}
