package org.FunMine.FMCaptcha.listeners;

import org.FunMine.FMCaptcha.manager.CaptchaManager;
import org.FunMine.FMCaptcha.manager.ConfigManager;
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
    private final CaptchaManager captchaManager;
    private final boolean freezeMovement;
    private final boolean blockCommands;
    private final boolean blockChat;

    public PlayerListener(JavaPlugin plugin, CaptchaManager captchaManager) {
        this.plugin = plugin;
        this.captchaManager = captchaManager;
        ConfigManager config = captchaManager.getConfig();
        this.freezeMovement = config.isFreezeMovement();
        this.blockCommands = config.isBlockCommands();
        this.blockChat = config.isBlockChat();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                captchaManager.startCapchaCheck(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!freezeMovement) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (captchaManager.isPending(playerId) &&
                (event.getFrom().getX() != event.getTo().getX() ||
                        event.getFrom().getZ() != event.getTo().getZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!blockCommands) return;

        Player player = event.getPlayer();
        if (captchaManager.isPending(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (captchaManager.isPending(playerId)) {
            if (blockChat) {
                event.setCancelled(true);
            }

            String message = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (captchaManager.isPending(playerId)) {
                    captchaManager.checkCapcha(player, message);
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
        captchaManager.cleanupPlayer(playerId);
    }
}
