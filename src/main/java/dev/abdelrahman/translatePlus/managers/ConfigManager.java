package dev.abdelrahman.translatePlus.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import dev.abdelrahman.translatePlus.TranslatePlus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private final TranslatePlus plugin;
    private FileConfiguration config;

    // Cache for messages to avoid repeated file reads
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    // Cache for configuration values
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();

    public ConfigManager(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Clear caches when loading
        messageCache.clear();
        configCache.clear();

        // Basic settings defaults
        config.addDefault("translation-service", "google"); //  service selection
        config.addDefault("google-api-key", "PUT-YOUR-GOOGLE-KEY-HERE");
        config.addDefault("openai-api-key", "PUT-YOUR-OPENAI-KEY-HERE"); //  OpenAI key
        config.addDefault("default-language", "en");
        config.addDefault("translate-commands", false);
        config.addDefault("debug-mode", false);

        // Updated messages with service support
        config.addDefault("messages.prefix", "&7[&aTranslatePlus&7] ");
        config.addDefault("messages.enabled", "&aTranslation enabled! Target language: %lang%");
        config.addDefault("messages.disabled", "&cTranslation disabled!");
        config.addDefault("messages.status", "&eYour translation is %status% (Language: %lang%)");
        config.addDefault("messages.no-api-key", "&cTranslation API key not set in config!");
        config.addDefault("messages.reload", "&aTranslatePlus config reloaded successfully.");
        config.addDefault("messages.default-set", "&aDefault language set to %lang%.");
        config.addDefault("messages.invalid-language", "&cInvalid language code: %lang%. Use /translate list to see available languages.");
        config.addDefault("messages.translation-error", "&cTranslation error occurred! Check console for details.");
        config.addDefault("messages.no-permission", "&cYou don't have permission to use this command!");
        config.addDefault("messages.usage", "&eUsage: /translate <on|off|status|help|list|reload|setdefault|>");
        config.addDefault("messages.api-quota-exceeded", "&cAPI quota exceeded! Please try again later.");
        config.addDefault("messages.player-only", "&cThis command can only be used by players!");
        config.addDefault("messages.already-enabled", "&eTranslation is already enabled for %lang%!");
        config.addDefault("messages.already-disabled", "&eTranslation is already disabled!");
        config.addDefault("messages.translation-timeout", "&cTranslation request timed out. Please try again.");
        config.addDefault("messages.rate-limit-exceeded", "&cYou are sending messages too quickly! Please slow down.");

        // Advanced settings defaults
        config.addDefault("advanced.cache-size", 1000);
        config.addDefault("advanced.connection-timeout", 5000);
        config.addDefault("advanced.read-timeout", 10000);
        config.addDefault("advanced.max-message-length", 500);
        config.addDefault("advanced.min-message-length", 2);
        config.addDefault("advanced.rate-limit-per-minute", 30);
        config.addDefault("advanced.translate-english-text", true);
        config.addDefault("advanced.cache-cleanup-interval", 60);

        // Service-specific settings
        config.addDefault("service-settings.google.use-base-model", true);
        config.addDefault("service-settings.google.format", "text");
        config.addDefault("service-settings.openai.model", "gpt-3.5-turbo");
        config.addDefault("service-settings.openai.max-tokens", 150);
        config.addDefault("service-settings.openai.temperature", 0.1);

        // Translation blacklist defaults
        config.addDefault("translation-blacklist", List.of(
                "server", "minecraft", "bukkit", "spigot", "paper", "plugin", "mod",
                "admin", "op", "operator", "whitelist", "ban", "kick", "mute",
                "tempban", "unban", "unmute", "gamemode", "creative", "survival", "spectator", "adventure"
        ));

        // Language settings defaults
        config.addDefault("language-settings.special-handling.rtl-languages",
                List.of("ar", "he", "fa", "ur", "ps", "sd"));
        config.addDefault("language-settings.special-handling.verbose-languages",
                List.of("de", "fi", "hu", "ja", "ko", "th", "vi"));

        // Performance settings
        config.addDefault("performance.async-translation", true);
        config.addDefault("performance.batch-translations", true);
        config.addDefault("performance.max-concurrent-translations", 10);

        config.options().copyDefaults(true);
        plugin.saveConfig();

        // Pre-cache commonly used values
        cacheCommonValues();
    }

    /**
     * Cache commonly used configuration values for better performance
     */
    private void cacheCommonValues() {
        configCache.put("translation-service", config.getString("translation-service", "google"));
        configCache.put("google-api-key", config.getString("google-api-key", "PUT-YOUR-GOOGLE-KEY-HERE"));
        configCache.put("openai-api-key", config.getString("openai-api-key", "PUT-YOUR-OPENAI-KEY-HERE"));
        configCache.put("default-language", config.getString("default-language", "en"));
        configCache.put("translate-commands", config.getBoolean("translate-commands", false));
        configCache.put("debug-mode", config.getBoolean("debug-mode", false));
        configCache.put("rate-limit", config.getInt("advanced.rate-limit-per-minute", 30));
        configCache.put("max-message-length", config.getInt("advanced.max-message-length", 500));
        configCache.put("min-message-length", config.getInt("advanced.min-message-length", 2));
        configCache.put("rtl-languages", config.getStringList("language-settings.special-handling.rtl-languages"));
        configCache.put("translation-blacklist", config.getStringList("translation-blacklist"));
        configCache.put("verbose-languages", config.getStringList("language-settings.special-handling.verbose-languages"));
        configCache.put("translate-english", config.getBoolean("advanced.translate-english-text", true));

        // Service-specific caching
        configCache.put("openai-model", config.getString("service-settings.openai.model", "gpt-3.5-turbo"));
        configCache.put("openai-max-tokens", config.getInt("service-settings.openai.max-tokens", 150));
        configCache.put("openai-temperature", config.getDouble("service-settings.openai.temperature", 0.1));
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Clear caches when reloading
        messageCache.clear();
        configCache.clear();

        // Re-cache values
        cacheCommonValues();

        debugLog("Configuration reloaded and caches cleared");
    }

    // Translation service configuration
    public String getTranslationService() {
        return (String) configCache.computeIfAbsent("translation-service",
                k -> config.getString("translation-service", "google"));
    }

    public void setTranslationService(String service) {
        if (!"google".equals(service) && !"openai".equals(service)) {
            throw new IllegalArgumentException("Invalid translation service: " + service + ". Must be 'google' or 'openai'");
        }
        config.set("translation-service", service);
        plugin.saveConfig();
        configCache.put("translation-service", service);
        debugLog("Translation service set to: " + service);
    }

    // Basic configuration getters with caching
    public String getApiKey() {
        String service = getTranslationService();
        if ("openai".equals(service)) {
            return getOpenAIApiKey();
        }
        return getGoogleApiKey();
    }

    public String getGoogleApiKey() {
        return (String) configCache.computeIfAbsent("google-api-key",
                k -> config.getString("google-api-key", "PUT-YOUR-GOOGLE-KEY-HERE"));
    }

    public String getOpenAIApiKey() {
        return (String) configCache.computeIfAbsent("openai-api-key",
                k -> config.getString("openai-api-key", "PUT-YOUR-OPENAI-KEY-HERE"));
    }

    public String getDefaultLanguage() {
        return (String) configCache.computeIfAbsent("default-language",
                k -> config.getString("default-language", "en"));
    }

    public boolean isTranslateCommandsEnabled() {
        return (Boolean) configCache.computeIfAbsent("translate-commands",
                k -> config.getBoolean("translate-commands", false));
    }

    public boolean isDebugModeEnabled() {
        return (Boolean) configCache.computeIfAbsent("debug-mode",
                k -> config.getBoolean("debug-mode", false));
    }

    public void setDefaultLanguage(String language) {
        config.set("default-language", language);
        plugin.saveConfig();
        configCache.put("default-language", language);
        debugLog("Default language set to: " + language);
    }

    // OpenAI-specific configuration
    public String getOpenAIModel() {
        return (String) configCache.computeIfAbsent("openai-model",
                k -> config.getString("service-settings.openai.model", "gpt-3.5-turbo"));
    }

    public int getOpenAIMaxTokens() {
        return (Integer) configCache.computeIfAbsent("openai-max-tokens",
                k -> config.getInt("service-settings.openai.max-tokens", 150));
    }

    public double getOpenAITemperature() {
        return (Double) configCache.computeIfAbsent("openai-temperature",
                k -> config.getDouble("service-settings.openai.temperature", 0.1));
    }

    // Advanced settings getters with caching
    public int getCacheSize() {
        return (Integer) configCache.computeIfAbsent("cache-size",
                k -> config.getInt("advanced.cache-size", 1000));
    }

    public int getConnectionTimeout() {
        return (Integer) configCache.computeIfAbsent("connection-timeout",
                k -> config.getInt("advanced.connection-timeout", 5000));
    }

    public int getReadTimeout() {
        return (Integer) configCache.computeIfAbsent("read-timeout",
                k -> config.getInt("advanced.read-timeout", 10000));
    }

    public int getMaxMessageLength() {
        return (Integer) configCache.computeIfAbsent("max-message-length",
                k -> config.getInt("advanced.max-message-length", 500));
    }

    public int getMinMessageLength() {
        return (Integer) configCache.computeIfAbsent("min-message-length",
                k -> config.getInt("advanced.min-message-length", 2));
    }

    public int getRateLimitPerMinute() {
        return (Integer) configCache.computeIfAbsent("rate-limit",
                k -> config.getInt("advanced.rate-limit-per-minute", 30));
    }

    public boolean shouldTranslateEnglishText() {
        return (Boolean) configCache.computeIfAbsent("translate-english",
                k -> config.getBoolean("advanced.translate-english-text", true));
    }

    public int getCacheCleanupInterval() {
        return (Integer) configCache.computeIfAbsent("cache-cleanup-interval",
                k -> config.getInt("advanced.cache-cleanup-interval", 60));
    }

    // Performance settings
    public boolean isAsyncTranslationEnabled() {
        return (Boolean) configCache.computeIfAbsent("async-translation",
                k -> config.getBoolean("performance.async-translation", true));
    }

    public boolean isBatchTranslationsEnabled() {
        return (Boolean) configCache.computeIfAbsent("batch-translations",
                k -> config.getBoolean("performance.batch-translations", true));
    }

    public int getMaxConcurrentTranslations() {
        return (Integer) configCache.computeIfAbsent("max-concurrent-translations",
                k -> config.getInt("performance.max-concurrent-translations", 10));
    }

    // Blacklist and special handling with caching
    @SuppressWarnings("unchecked")
    public List<String> getTranslationBlacklist() {
        return (List<String>) configCache.computeIfAbsent("translation-blacklist",
                k -> config.getStringList("translation-blacklist"));
    }

    @SuppressWarnings("unchecked")
    public List<String> getRtlLanguages() {
        return (List<String>) configCache.computeIfAbsent("rtl-languages",
                k -> config.getStringList("language-settings.special-handling.rtl-languages"));
    }

    @SuppressWarnings("unchecked")
    public List<String> getVerboseLanguages() {
        return (List<String>) configCache.computeIfAbsent("verbose-languages",
                k -> config.getStringList("language-settings.special-handling.verbose-languages"));
    }

    // Helper methods for blacklist checking
    public boolean isBlacklisted(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase();
        List<String> blacklist = getTranslationBlacklist();

        for (String blacklistedWord : blacklist) {
            if (lowerText.contains(blacklistedWord.toLowerCase())) {
                debugLog("Message blacklisted due to word: " + blacklistedWord);
                return true;
            }
        }

        return false;
    }

    public boolean isRightToLeftLanguage(String languageCode) {
        boolean isRTL = getRtlLanguages().contains(languageCode.toLowerCase());
        if (isRTL) {
            debugLog("Language " + languageCode + " is RTL");
        }
        return isRTL;
    }

    public boolean isVerboseLanguage(String languageCode) {
        boolean isVerbose = getVerboseLanguages().contains(languageCode.toLowerCase());
        if (isVerbose) {
            debugLog("Language " + languageCode + " is verbose");
        }
        return isVerbose;
    }

    // Message handling methods with caching
    public String getMessage(String key) {
        return getMessage(key, (String[]) null);
    }

    public String getMessage(String key, String placeholder, String value) {
        return getMessage(key, new String[]{placeholder, value});
    }

    public String getMessage(String key, String... replacements) {
        // Create cache key that includes replacements for uniqueness
        String cacheKey = key;
        if (replacements != null && replacements.length > 0) {
            cacheKey += "|" + String.join("|", replacements);
        }

        return messageCache.computeIfAbsent(cacheKey, k -> {
            String message = config.getString("messages." + key, "&cMessage not found: " + key);
            String prefix = config.getString("messages.prefix", "&7[&aTranslatePlus&7] ");

            message = prefix + message;

            // Apply replacements in pairs (placeholder, value)
            if (replacements != null && replacements.length >= 2) {
                for (int i = 0; i < replacements.length - 1; i += 2) {
                    String placeholder = replacements[i];
                    String value = replacements[i + 1];
                    if (placeholder != null && value != null) {
                        message = message.replace("%" + placeholder + "%", value);
                    }
                }
            }

            return ChatColor.translateAlternateColorCodes('&', message);
        });
    }

    public String getMessage(String key, String[][] replacements) {
        if (replacements == null || replacements.length == 0) {
            return getMessage(key);
        }

        // Convert 2D array to flat array for the main method
        String[] flatReplacements = new String[replacements.length * 2];
        int index = 0;
        for (String[] replacement : replacements) {
            if (replacement.length >= 2) {
                flatReplacements[index++] = replacement[0];
                flatReplacements[index++] = replacement[1];
            }
        }

        // Create array with correct size
        String[] finalReplacements = new String[index];
        System.arraycopy(flatReplacements, 0, finalReplacements, 0, index);

        return getMessage(key, finalReplacements);
    }

    // Debug logging helper
    public void debugLog(String message) {
        if (isDebugModeEnabled()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    // Enhanced validation methods
    public boolean isValidMessageLength(String message) {
        if (message == null) {
            debugLog("Message validation failed: null message");
            return false;
        }

        int length = message.trim().length();
        int minLength = getMinMessageLength();
        int maxLength = getMaxMessageLength();

        boolean isValid = length >= minLength && length <= maxLength;

        if (!isValid) {
            debugLog("Message validation failed: length " + length + " not between " + minLength + " and " + maxLength);
        }

        return isValid;
    }

    public String truncateMessage(String message) {
        if (message == null) {
            return "";
        }

        int maxLength = getMaxMessageLength();
        if (message.length() <= maxLength) {
            return message;
        }

        String truncated = message.substring(0, maxLength - 3) + "...";
        debugLog("Message truncated from " + message.length() + " to " + truncated.length() + " characters");
        return truncated;
    }

    /**
     * Check if the API key is properly configured for the current service
     */
    public boolean isApiKeyConfigured() {
        String service = getTranslationService();
        String apiKey = getApiKey();

        boolean isConfigured;
        if ("google".equals(service)) {
            isConfigured = apiKey != null && !apiKey.equals("PUT-YOUR-GOOGLE-KEY-HERE") && !apiKey.trim().isEmpty();
        } else if ("openai".equals(service)) {
            isConfigured = apiKey != null && !apiKey.equals("PUT-YOUR-OPENAI-KEY-HERE") &&
                    !apiKey.trim().isEmpty() && apiKey.startsWith("sk-") && apiKey.length() > 20;
        } else {
            isConfigured = false;
        }

        if (!isConfigured) {
            debugLog("API key is not properly configured for service: " + service);
        }

        return isConfigured;
    }

    /**
     * Get cache statistics for debugging
     */
    public void logCacheStats() {
        if (isDebugModeEnabled()) {
            debugLog("Message cache size: " + messageCache.size());
            debugLog("Config cache size: " + configCache.size());
            debugLog("Current translation service: " + getTranslationService());
        }
    }

    /**
     * Clear message cache manually
     */
    public void clearMessageCache() {
        messageCache.clear();
        debugLog("Message cache cleared");
    }

    /**
     * Check if a language code appears to be valid (basic validation)
     */
    public boolean isValidLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }

        // Basic validation: should be 2-5 characters, letters and hyphens only
        return languageCode.matches("^[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?$");
    }
}