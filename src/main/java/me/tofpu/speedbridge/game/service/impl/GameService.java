package me.tofpu.speedbridge.game.service.impl;

import me.tofpu.speedbridge.SpeedBridge;
import me.tofpu.speedbridge.data.file.config.path.Path;
import me.tofpu.speedbridge.game.result.Result;
import me.tofpu.speedbridge.game.service.IGameService;
import me.tofpu.speedbridge.island.IIsland;
import me.tofpu.speedbridge.island.mode.Mode;
import me.tofpu.speedbridge.island.properties.twosection.TwoSection;
import me.tofpu.speedbridge.island.service.IIslandService;
import me.tofpu.speedbridge.lobby.service.ILobbyService;
import me.tofpu.speedbridge.user.IUser;
import me.tofpu.speedbridge.user.properties.UserProperties;
import me.tofpu.speedbridge.user.properties.timer.Timer;
import me.tofpu.speedbridge.user.service.IUserService;
import me.tofpu.speedbridge.util.Cuboid;
import me.tofpu.speedbridge.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameService implements IGameService {
    private final IIslandService islandService;
    private final IUserService userService;
    private final ILobbyService lobbyService;

    private final Map<UUID, Timer> userTimer = new HashMap<>();
    private final Map<UUID, BukkitTask> userCheck = new HashMap<>();

    public GameService(final IIslandService islandService, final IUserService userService, final ILobbyService lobbyService) {
        this.islandService = islandService;
        this.userService = userService;
        this.lobbyService = lobbyService;
    }

    @Override
    public Result join(final Player player) {
        return join(player, null);
    }

    @Override
    public Result join(final Player player, final int slot) {
        if (!lobbyService.hasLobbyLocation()) {
            return Result.INVALID_LOBBY;
        }

        final IUser user = userService.getOrDefault(player.getUniqueId());
        if (user.getProperties().getIslandSlot() != null) return Result.DENY;

        return join(user, islandService.getIslandBySlot(slot));
    }

    @Override
    public Result join(final Player player, final Mode mode) {
        if (!lobbyService.hasLobbyLocation()) {
            return Result.INVALID_LOBBY;
        }

        final IUser user = userService.getOrDefault(player.getUniqueId());
        if (user.getProperties().getIslandSlot() != null) return Result.DENY;

        final List<IIsland> islands = mode == null ? islandService.getAvailableIslands() : islandService.getAvailableIslands(mode);
        if (islands.size() < 1) return Result.FULL;

        return join(user, islands.get(0));
    }

    @Override
    public Result join(final IUser user, IIsland island) {
        if (!lobbyService.hasLobbyLocation()) {
            return Result.INVALID_LOBBY;
        }

        if (island == null) return Result.DENY;
        else if (!island.isAvailable()) return Result.FULL;

        user.getProperties().setIslandSlot(island.getSlot());
        island.setTakenBy(user);

        final Player player = Bukkit.getPlayer(user.getUuid());
        if (player == null) return Result.DENY;
        player.teleport(island.getLocation());

        final Inventory inventory = player.getInventory();
        inventory.clear();

        player.getActivePotionEffects().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        inventory.addItem(new ItemStack(Material.WOOL, 64));

        final TwoSection selection = (TwoSection) island.getProperties().get("selection");
        final Cuboid cuboid = new Cuboid(selection.getPointA(), selection.getPointB());

        //TODO: FIX THIS
        this.userCheck.put(player.getUniqueId(),
                Bukkit.getScheduler()
                        .runTaskTimer(
                                SpeedBridge.getProvidingPlugin(SpeedBridge.class),
                                () -> {
                                    if (!cuboid.isIn(player.getLocation())) {
                                        reset(user);
                                    }
                                },
                                20, 10));
        return Result.SUCCESS;
    }

    @Override
    public Result leave(final Player player) {
        final IUser user = userService.searchForUUID(player.getUniqueId());
        if (user == null) return Result.DENY;
        player.getInventory().clear();

        islandService.resetIsland(user.getProperties().getIslandSlot());
        user.getProperties().setIslandSlot(null);

        userTimer.remove(player.getUniqueId());
        userCheck.get(player.getUniqueId()).cancel();
        userCheck.remove(player.getUniqueId());

        player.teleport(lobbyService.getLobbyLocation());
        Util.message(player, Path.MESSAGES_LEFT);

        return Result.SUCCESS;
    }

    @Override
    public boolean isPlaying(final Player player) {
        final IUser user;
        if ((user = userService.searchForUUID(player.getUniqueId())) == null) return false;
        final Integer islandSlot = user.getProperties().getIslandSlot();
        if (islandSlot == null) return false;

        return islandService.getIslandBySlot(islandSlot) != null;
    }

    @Override
    public void addTimer(final IUser user) {
        final Timer timer = new Timer(user.getProperties().getIslandSlot());

        this.userTimer.put(user.getUuid(), timer);
    }

    @Override
    public boolean hasTimer(final IUser user) {
        return this.userTimer.containsKey(user.getUuid());
    }

    @Override
    public Timer getTimer(IUser user) {
        return userTimer.get(user.getUuid());
    }

    @Override
    public void updateTimer(final IUser user) {
        if (user == null) return;
        final UserProperties properties = user.getProperties();
        final Timer lowestTimer = properties.getTimer();
        final Player player = Bukkit.getPlayer(user.getUuid());
        player.sendMessage("Phase One!");

        final Timer gameTimer = userTimer.get(user.getUuid());
        gameTimer.setEnd(System.currentTimeMillis());
        gameTimer.complete();
        player.sendMessage("Phase Two!");

        final Map<String, Double> replace = new HashMap<>();
        replace.put("%scored%", gameTimer.getResult());
        Util.message(player, Path.MESSAGES_SCORED, replace);
        replace.clear();
        player.sendMessage("Phase Three!");

        if (lowestTimer != null && lowestTimer.getResult() <= gameTimer.getResult()) {
            replace.put("%score%", lowestTimer.getResult());
            Util.message(player, Path.MESSAGES_NOT_BEATEN, replace);
        } else {
            if (lowestTimer != null) {
                replace.put("%calu_score%", lowestTimer.getResult() - gameTimer.getResult());
                Util.message(player, Path.MESSAGES_BEATEN_SCORE, replace);
            }

            properties.setTimer(gameTimer);
            lobbyService.getLeaderboard().check(user);
        }

        player.sendMessage("Phase Four!");
        reset(user);
    }

    @Override
    public void resetTimer(final IUser user) {
        if (user == null) return;

        userTimer.remove(user.getUuid());
        islandService.resetBlocks(islandService.getIslandBySlot(user.getProperties().getIslandSlot()));
    }

    @Override
    public void reset(final IUser user) {
        if (user == null) return;

        resetTimer(user);

        final Player player = Bukkit.getPlayer(user.getUuid());
        if (player == null) return;
        player.sendMessage("Phase Sixth!");

        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(islandService.getIslandBySlot(user.getProperties().getIslandSlot()).getLocation());
        player.sendMessage("Phase Seven!");
    }
}
