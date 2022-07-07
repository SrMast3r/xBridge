package me.tofpu.speedbridge.game.process.processors;

import me.tofpu.speedbridge.api.island.Island;
import me.tofpu.speedbridge.api.user.User;
import me.tofpu.speedbridge.game.process.ProcessType;
import me.tofpu.speedbridge.game.process.Process;
import me.tofpu.speedbridge.game.process.type.GameProcessor;
import me.tofpu.speedbridge.game.service.GameServiceImpl;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class JoinProcessor extends GameProcessor {
    @Override
    public void process(final GameServiceImpl gameService,
                        final Island island, final User user,
                        final Player player,
                        final ProcessType type) {

        switch (type) {
            case PROCESS:
                // setting the user properties island slot
                // to this island's slot for tracking purposes
                user.properties().islandSlot(island.slot());

                // setting the island takenBy to this user
                // for availability reasons
                island.takenBy(user);
                //  player.sendMessage("§e§lBRIDGE §8» §fListo para jugar %player% ");

                player.performCommand("say " + player.getName());

                player.teleport(island.location());
                player.getActivePotionEffects().clear();
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                gameService.gameChecker().put(user, island);
                if (gameService.runnable().isPaused()) {
                    gameService.runnable().resume();
                }
                break;
            case REVERSE:
                gameService.resetIsland(user.properties().islandSlot());
                user.properties().islandSlot(null);
                gameService.gameTimer().remove(player.getUniqueId());
                gameService.gameChecker().remove(user);

                if (gameService.gameChecker().isEmpty()) {
                    gameService.runnable().pause();
                }

                player.teleport(gameService.lobbyService().getLobbyLocation());
                break;
        }
        // processing the items to this user
        Process.ITEM_JOIN.process(user, player, type);
    }
}
