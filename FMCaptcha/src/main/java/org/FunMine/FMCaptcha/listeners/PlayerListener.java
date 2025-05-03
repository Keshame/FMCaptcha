package org.FunMine.FMCaptcha.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;

    private final Map<UUID, String> pendingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attemptsLeft = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> reminderTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerColors = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerUnicodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> captchaStartTimes = new ConcurrentHashMap<>();

    public CaptchaManager(JavaPlugin plugin, ConfigManager configManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
    }

    public void startCapchaCheck(Player player) {
        UUID playerId = player.getUniqueId();
        cleanupPlayer(playerId);

        if (!databaseManager.shouldCheckPlayer(playerId)) {
            executeSuccessCommands(player);
            return;
        }

        String color = configManager.getRandomColor();
        String unicode = configManager.getRandomUnicode();

        playerColors.put(playerId, color);
        playerUnicodes.put(playerId, unicode);

        Map.Entry<String, String> capchaEntry = configManager.getRandomCapchaPair();
        String randomKey = capchaEntry.getKey();
        String correctAnswer = capchaEntry.getValue();

        pendingPlayers.put(playerId, correctAnswer);
        attemptsLeft.put(playerId, configManager.getAttempts());
        captchaStartTimes.put(playerId, System.currentTimeMillis());

        applyPendingEffects(player);
        sendCapchaMessages(player, randomKey);
        setupTimeout(player);
    }

    private void sendCapchaMessages(Player player, String capchaText) {
        UUID playerId = player.getUniqueId();
        String color = playerColors.get(playerId);
        String unicode = playerUnicodes.get(playerId);

        String message = configManager.getCapchaPromptMessage(capchaText, color, unicode);
        player.sendMessage(message);

        if (configManager.isTitlesEnabled()) {
            player.sendTitle(
                    configManager.getCapchaTitle(),
                    configManager.getCapchaSubtitle(),
                    configManager.getTitleFadeIn(),
                    configManager.getTitleStay(),
                    configManager.getTitleFadeOut()
            );
        }

        playSound(player, "on-start");

        if (configManager.getReminderInterval() > 0) {
            startReminderTask(player, capchaText);
        }
    }

    private void startReminderTask(Player player, String capchaText) {
        UUID playerId = player.getUniqueId();
        String color = playerColors.get(playerId);
        String unicode = playerUnicodes.get(playerId);

        if (reminderTasks.containsKey(playerId)) {
            reminderTasks.get(playerId).cancel();
        }

        int interval = configManager.getReminderInterval() * 20;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (pendingPlayers.containsKey(playerId)) {
                String reminder = configManager.getCapchaReminderMessage(capchaText, color, unicode);
                player.sendMessage(reminder);
                playSound(player, "on-reminder");
            }
        }, interval, interval);

        reminderTasks.put(playerId, task);
    }

    private void handleCapchaSuccess(Player player) {
        UUID playerId = player.getUniqueId();
        cleanupPlayer(playerId);
        databaseManager.savePlayerResult(playerId, true);

        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        player.sendMessage(configManager.getCapchaSuccessMessage());
        if (configManager.isTitlesEnabled()) {
            player.sendTitle(
                    configManager.getCapchaSuccessTitle(),
                    configManager.getCapchaSuccessSubtitle(),
                    configManager.getTitleFadeIn(),
                    configManager.getTitleStay(),
                    configManager.getTitleFadeOut()
            );
        }

        playSound(player, "on-success");
        executeSuccessCommands(player);
    }

    private void executeSuccessCommands(Player player) {
        configManager.getSuccessCommands().stream()
                .filter(command -> command != null && !command.trim().isEmpty())
                .forEach(command ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("$player", player.getName()))
                );
    }

    private void applyPendingEffects(Player player) {
        configManager.getPendingEffects().forEach(effect -> player.addPotionEffect(effect, true));
    }

    public boolean checkCapcha(Player player, String input) {
        UUID playerId = player.getUniqueId();
        if (!pendingPlayers.containsKey(playerId)) return false;

        Long startTime = captchaStartTimes.get(playerId);
        if (startTime != null && System.currentTimeMillis() - startTime < 1200) {
            handleCapchaFail(player, true);
            return false;
        }

        String correctAnswer = pendingPlayers.get(playerId);

        if (input.equalsIgnoreCase(correctAnswer)) {
            handleCapchaSuccess(player);
            return true;
        }
        else if (configManager.isTrapWord(input)) {
            handleCapchaFail(player, true);
            return false;
        } else {
            handleCapchaFail(player, false);
            return false;
        }
    }

    private void handleCapchaFail(Player player, boolean isTrap) {
        UUID playerId = player.getUniqueId();
        int attempts = attemptsLeft.get(playerId) - 1;
        attemptsLeft.put(playerId, attempts);

        if (attempts <= 0 || isTrap) {
            player.kickPlayer(configManager.getCapchaKickMessage());
            playSound(player, "on-kick");
            cleanupPlayer(playerId);
            databaseManager.savePlayerResult(playerId, false);
        } else {
            player.sendMessage(configManager.getCapchaFailMessage());
            if (configManager.isTitlesEnabled()) {
                player.sendTitle(
                        configManager.getCapchaFailTitle(),
                        configManager.getCapchaFailSubtitle(),
                        configManager.getTitleFadeIn(),
                        configManager.getTitleStay(),
                        configManager.getTitleFadeOut()
                );
            }
            playSound(player, "on-fail");
        }
    }

    private void setupTimeout(Player player) {
        UUID playerId = player.getUniqueId();

        if (timeoutTasks.containsKey(playerId)) {
            timeoutTasks.get(playerId).cancel();
        }

        int delay = configManager.getDelaySeconds() * 20;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingPlayers.containsKey(playerId)) {
                player.kickPlayer(configManager.getCapchaKickMessage());
                cleanupPlayer(playerId);
                databaseManager.savePlayerResult(playerId, false);
            }
        }, delay);

        timeoutTasks.put(playerId, task);
    }

    public void cleanupPlayer(UUID playerId) {
        pendingPlayers.remove(playerId);
        attemptsLeft.remove(playerId);
        playerColors.remove(playerId);
        playerUnicodes.remove(playerId);
        captchaStartTimes.remove(playerId);

        BukkitTask timeoutTask = timeoutTasks.remove(playerId);
        if (timeoutTask != null) timeoutTask.cancel();

        BukkitTask reminderTask = reminderTasks.remove(playerId);
        if (reminderTask != null) reminderTask.cancel();
    }

    public void cleanupAllPlayers() {
        new HashSet<>(pendingPlayers.keySet()).forEach(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.getActivePotionEffects().forEach(effect ->
                        player.removePotionEffect(effect.getType()));
            }
            cleanupPlayer(playerId);
        });
    }

    public boolean isPending(UUID playerId) {
        return pendingPlayers.containsKey(playerId);
    }

    private void playSound(Player player, String soundName) {
        ConfigManager.SoundConfig soundConfig = configManager.getSoundConfig(soundName);
        if (soundConfig != null) {
            player.playSound(
                    player.getLocation(),
                    soundConfig.sound,
                    soundConfig.volume,
                    soundConfig.pitch
            );
        }
    }

    public ConfigManager getConfig() {
        return configManager;
    }
}
