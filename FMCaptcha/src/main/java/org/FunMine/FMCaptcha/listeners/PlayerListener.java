package org.FunMine.FMCaptcha.listeners;

import org.FunMine.FMCaptcha.manager.CaptchaManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final CaptchaManager capchaManager;

    public PlayerListener(JavaPlugin plugin, CaptchaManager capchaManager) {
        this.plugin = plugin;
        this.capchaManager = capchaManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                capchaManager.startCapchaCheck(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (capchaManager.isPending(playerId) &&
                capchaManager.getConfig().isFreezeMovement() &&
                (event.getFrom().getX() != event.getTo().getX() ||
                        event.getFrom().getZ() != event.getTo().getZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (capchaManager.isPending(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (capchaManager.isPending(playerId)) {
            event.setCancelled(true);
            String message = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (capchaManager.isPending(playerId)) {
                    capchaManager.checkCapcha(player, message);
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerDisconnect(event.getPlayer());
    }

    private void handlePlayerDisconnect(Player player) {
        UUID playerId = player.getUniqueId();

        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );
        capchaManager.cleanupPlayer(playerId);
    }
}