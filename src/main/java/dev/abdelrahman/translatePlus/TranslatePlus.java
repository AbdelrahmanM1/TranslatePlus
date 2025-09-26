package dev.abdelrahman.translatePlus;

import dev.abdelrahman.translatePlus.commands.TranslateCommand;
import dev.abdelrahman.translatePlus.listeners.ChatListener;
import dev.abdelrahman.translatePlus.managers.ConfigManager;
import dev.abdelrahman.translatePlus.managers.PlayerManager;
import dev.abdelrahman.translatePlus.services.GoogleTranslateService;
import org.bukkit.plugin.java.JavaPlugin;

public final class TranslatePlus extends JavaPlugin {

    private static TranslatePlus instance;
    private ConfigManager configManager;
    private PlayerManager playerManager;
    private GoogleTranslateService translateService;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers and services
        this.configManager = new ConfigManager(this);
        this.playerManager = new PlayerManager();
        this.translateService = new GoogleTranslateService(this);

        // Load config
        configManager.loadConfig();

        // Validate API key
        if (!translateService.validateApiKey()) {
            getLogger().severe("Google Translate API key is not set or invalid! Please configure it in config.yml");
        }

        // Register commands
        getCommand("translate").setExecutor(new TranslateCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("TranslatePlus has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clear player data
        if (playerManager != null) {
            playerManager.clearAll();
        }

        getLogger().info("TranslatePlus has been disabled!");
    }

    // Getters for managers and services
    public static TranslatePlus getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public GoogleTranslateService getTranslateService() {
        return translateService;
    }
}