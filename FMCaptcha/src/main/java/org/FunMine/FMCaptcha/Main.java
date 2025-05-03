package org.FunMine.FMCaptcha;

import org.FunMine.FMCaptcha.listeners.PlayerListener;
import org.FunMine.FMCaptcha.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CaptchaManager capchaManager;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        setupManagers();
        registerEvents();

        getLogger().info("Плагин капчи успешно запущен!");
    }

    @Override
    public void onDisable() {
        capchaManager.cleanupAllPlayers();
        databaseManager.shutdown();

        getLogger().info("Плагин капчи успешно выключен");
    }

    private void setupManagers() {
        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        capchaManager = new CaptchaManager(this, configManager, databaseManager);
    }

    private void registerEvents() {
        playerListener = new PlayerListener(this, capchaManager);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }
}
