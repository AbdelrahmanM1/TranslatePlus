package dev.abdelrahman.translatePlus;

import dev.abdelrahman.translatePlus.commands.TranslateCommand;
import dev.abdelrahman.translatePlus.listeners.ChatListener;
import dev.abdelrahman.translatePlus.managers.ConfigManager;
import dev.abdelrahman.translatePlus.managers.PlayerManager;
import dev.abdelrahman.translatePlus.services.GoogleTranslateService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TranslatePlus extends JavaPlugin {

    private static TranslatePlus instance;
    private ConfigManager configManager;
    private PlayerManager playerManager;
    private GoogleTranslateService translateService;

    // Plugin state tracking
    private boolean fullyLoaded = false;
    private boolean initializationFailed = false;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        getLogger().info("Starting TranslatePlus v" + getDescription().getVersion() + "...");

        try {
            // Step 1: Initialize ConfigManager first
            getLogger().info("Initializing ConfigManager...");
            this.configManager = new ConfigManager(this);

            // Step 2: Load configuration before anything else
            getLogger().info("Loading configuration...");
            configManager.loadConfig();
            configManager.debugLog("Configuration loaded successfully");

            // Step 3: Initialize PlayerManager with plugin reference
            getLogger().info("Initializing PlayerManager...");
            this.playerManager = new PlayerManager(this); // Pass 'this' instead of empty constructor
            configManager.debugLog("PlayerManager initialized");

            // Step 4: Initialize GoogleTranslateService (now config is available)
            getLogger().info("Initializing GoogleTranslateService...");
            this.translateService = new GoogleTranslateService(this);
            configManager.debugLog("GoogleTranslateService initialized");

            // Step 5: Validate API key configuration
            if (!configManager.isApiKeyConfigured()) {
                getLogger().warning("┌─────────────────────────────────────────┐");
                getLogger().warning("│  GOOGLE TRANSLATE API KEY NOT SET!     │");
                getLogger().warning("│                                         │");
                getLogger().warning("│  1. Get API key from Google Cloud      │");
                getLogger().warning("│  2. Set it in config.yml               │");
                getLogger().warning("│  3. Restart server                     │");
                getLogger().warning("│                                         │");
                getLogger().warning("│  Plugin will work but translations     │");
                getLogger().warning("│  will fail until API key is set!       │");
                getLogger().warning("└─────────────────────────────────────────┘");
            } else {
                // Optional: Test API key validity
                validateApiKeyAsync();
            }

            // Step 6: Register commands with null checks
            getLogger().info("Registering commands...");
            registerCommands();

            // Step 7: Register event listeners
            getLogger().info("Registering event listeners...");
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
            configManager.debugLog("ChatListener registered");

            // Step 8: Schedule async tasks
            scheduleAsyncTasks();

            // Mark as fully loaded
            fullyLoaded = true;
            long loadTime = System.currentTimeMillis() - startTime;

            getLogger().info("┌─────────────────────────────────────────┐");
            getLogger().info("│  TranslatePlus loaded successfully!    │");
            getLogger().info("│  Version: " + String.format("%-27s", getDescription().getVersion()) + " │");
            getLogger().info("│  Load time: " + String.format("%-24s", loadTime + "ms") + " │");
            getLogger().info("│  Debug mode: " + String.format("%-23s", configManager.isDebugModeEnabled() ? "ON" : "OFF") + " │");
            getLogger().info("└─────────────────────────────────────────┘");

        } catch (Exception e) {
            initializationFailed = true;
            getLogger().severe("┌─────────────────────────────────────────┐");
            getLogger().severe("│  CRITICAL ERROR DURING STARTUP!        │");
            getLogger().severe("│                                         │");
            getLogger().severe("│  TranslatePlus failed to initialize    │");
            getLogger().severe("│  Check the error below for details     │");
            getLogger().severe("│                                         │");
            getLogger().severe("│  Plugin will be disabled!              │");
            getLogger().severe("└─────────────────────────────────────────┘");
            getLogger().severe("Error details:");

            // Clean up any partially initialized components
            cleanup();

            // Disable the plugin
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down TranslatePlus...");

        cleanup();

        if (!initializationFailed && fullyLoaded) {
            getLogger().info("TranslatePlus disabled successfully!");
        } else {
            getLogger().info("TranslatePlus shutdown complete.");
        }
    }

    /**
     * Register plugin commands with proper error handling
     */
    private void registerCommands() {
        PluginCommand translateCmd = getCommand("translate");
        if (translateCmd != null) {
            TranslateCommand translateCommand = new TranslateCommand(this);
            translateCmd.setExecutor(translateCommand);
            translateCmd.setTabCompleter(translateCommand);
            configManager.debugLog("Translate command registered successfully");
        } else {
            getLogger().warning("Failed to register 'translate' command - not found in plugin.yml!");
        }
    }

    /**
     * Schedule async tasks for optimization
     */
    private void scheduleAsyncTasks() {
        if (configManager == null) return;

        // Cache cleanup task
        int cleanupInterval = configManager.getCacheCleanupInterval() * 60 * 20; // Convert minutes to ticks
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                if (configManager != null) {
                    configManager.logCacheStats();
                }
                if (translateService != null) {
                    translateService.performMaintenance(); // This method doesn't exist - we'll create it
                }
            } catch (Exception e) {
                getLogger().warning("Error during maintenance task: " + e.getMessage());
            }
        }, cleanupInterval, cleanupInterval);

        configManager.debugLog("Scheduled maintenance tasks");
    }

    /**
     * Validate API key asynchronously to avoid blocking startup
     */
    private void validateApiKeyAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (translateService != null && translateService.validateApiKey()) {
                    getLogger().info("API key validation successful!");
                } else {
                    getLogger().warning("API key validation failed - translations may not work!");
                }
            } catch (Exception e) {
                getLogger().warning("Could not validate API key: " + e.getMessage());
                if (configManager != null) {
                    configManager.debugLog("API validation error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Clean up resources during shutdown or failed initialization
     */
    private void cleanup() {
        try {
            // Clear player data
            if (playerManager != null) {
                playerManager.clearAll();
                if (configManager != null) {
                    configManager.debugLog("PlayerManager cleared");
                }
            }

            // Shutdown translation service - using existing methods
            if (translateService != null) {
                translateService.clearCache(); // Use existing method instead of shutdown()
                if (configManager != null) {
                    configManager.debugLog("GoogleTranslateService cache cleared");
                }
            }

            // Clear config caches
            if (configManager != null) {
                configManager.clearMessageCache();
                configManager.debugLog("ConfigManager caches cleared");
            }

        } catch (Exception e) {
            getLogger().warning("Error during cleanup: " + e.getMessage());
        }
    }

    // Getters with null safety checks
    public static TranslatePlus getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager not initialized - plugin startup may have failed!");
        }
        return configManager;
    }

    public PlayerManager getPlayerManager() {
        if (playerManager == null) {
            throw new IllegalStateException("PlayerManager not initialized - plugin startup may have failed!");
        }
        return playerManager;
    }

    public GoogleTranslateService getTranslateService() {
        if (translateService == null) {
            throw new IllegalStateException("GoogleTranslateService not initialized - plugin startup may have failed!");
        }
        return translateService;
    }

    /**
     * Check if the plugin is fully loaded and operational
     */
    public boolean isFullyLoaded() {
        return fullyLoaded && !initializationFailed;
    }

    /**
     * Check if initialization failed
     */
    public boolean hasInitializationFailed() {
        return initializationFailed;
    }

    /**
     * Get plugin statistics for debugging
     */
    public void logPluginStats() {
        if (configManager != null && configManager.isDebugModeEnabled()) {
            getLogger().info("=== TranslatePlus Statistics ===");
            getLogger().info("Fully loaded: " + fullyLoaded);
            getLogger().info("Initialization failed: " + initializationFailed);
            getLogger().info("API key configured: " + configManager.isApiKeyConfigured());

            if (playerManager != null) {
                // Fixed the method call - using the correct method name
                getLogger().info("Active players with translation: " + playerManager.getActiveTranslationCount());
            }

            if (translateService != null) {
                // Log translate service stats
                getLogger().info("Translation cache size: " + translateService.getCacheSize());
            }

            if (configManager != null) {
                configManager.logCacheStats();
            }
        }
    }
}