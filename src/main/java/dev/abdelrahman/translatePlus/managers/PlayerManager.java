package dev.abdelrahman.translatePlus.managers;

import dev.abdelrahman.translatePlus.TranslatePlus;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PlayerManager {

    private final TranslatePlus plugin;
    private final Map<UUID, String> playerLanguages;
    private final Map<UUID, Long> lastActivity;
    private final Set<UUID> disabledPlayers;

    // Statistics tracking
    private long totalTranslations = 0;
    private final Map<String, Integer> languageUsage = new ConcurrentHashMap<>();

    public PlayerManager(TranslatePlus plugin) {
        this.plugin = plugin;
        this.playerLanguages = new ConcurrentHashMap<>();
        this.lastActivity = new ConcurrentHashMap<>();
        this.disabledPlayers = new HashSet<>();
    }

    // Alternative constructor for backward compatibility
    public PlayerManager() {
        this.plugin = null;
        this.playerLanguages = new ConcurrentHashMap<>();
        this.lastActivity = new ConcurrentHashMap<>();
        this.disabledPlayers = new HashSet<>();
    }

    /**
     * Enable translation for a player with specified target language
     */
    public void enableTranslation(UUID playerId, String targetLanguage) {
        if (playerId == null || targetLanguage == null) {
            return;
        }

        String normalizedLanguage = targetLanguage.toLowerCase().trim();
        playerLanguages.put(playerId, normalizedLanguage);
        disabledPlayers.remove(playerId);
        updateActivity(playerId);

        // Track language usage statistics
        languageUsage.merge(normalizedLanguage, 1, Integer::sum);

        debugLog("Translation enabled for player " + playerId + " with language: " + normalizedLanguage);
    }

    /**
     * Disable translation for a player
     */
    public void disableTranslation(UUID playerId) {
        if (playerId == null) {
            return;
        }

        String language = playerLanguages.remove(playerId);
        disabledPlayers.add(playerId);
        updateActivity(playerId);

        // Update language usage statistics
        if (language != null) {
            languageUsage.computeIfPresent(language, (k, v) -> Math.max(0, v - 1));
        }

        debugLog("Translation disabled for player " + playerId);
    }

    /**
     * Check if translation is enabled for a player
     */
    public boolean isTranslationEnabled(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return playerLanguages.containsKey(playerId) && !disabledPlayers.contains(playerId);
    }

    /**
     * Get player's target language
     */
    public String getPlayerLanguage(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        String language = playerLanguages.get(playerId);
        if (language != null) {
            updateActivity(playerId);
        }
        return language;
    }

    /**
     * Get player's language or default if not set
     */
    public String getPlayerLanguageOrDefault(UUID playerId) {
        String playerLang = getPlayerLanguage(playerId);
        if (playerLang != null) {
            return playerLang;
        }

        // Get default from config if plugin is available
        if (plugin != null && plugin.getConfigManager() != null) {
            return plugin.getConfigManager().getDefaultLanguage();
        }

        return "en"; // Fallback default
    }

    /**
     * Set player's language without enabling translation
     */
    public void setPlayerLanguage(UUID playerId, String language) {
        if (playerId == null || language == null) {
            return;
        }

        String normalizedLanguage = language.toLowerCase().trim();
        if (isTranslationEnabled(playerId)) {
            playerLanguages.put(playerId, normalizedLanguage);
            languageUsage.merge(normalizedLanguage, 1, Integer::sum);
            updateActivity(playerId);
            debugLog("Language updated for player " + playerId + ": " + normalizedLanguage);
        }
    }

    /**
     * Get all players with translation enabled
     */
    public Map<UUID, String> getAllPlayers() {
        return new HashMap<>(playerLanguages);
    }

    /**
     * Get players using a specific language
     */
    public Set<UUID> getPlayersUsingLanguage(String language) {
        if (language == null) {
            return new HashSet<>();
        }

        String normalizedLanguage = language.toLowerCase().trim();
        return playerLanguages.entrySet().stream()
                .filter(entry -> normalizedLanguage.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Get online players with translation enabled
     */
    public Set<Player> getOnlinePlayersWithTranslation() {
        Set<Player> onlinePlayers = new HashSet<>();

        for (UUID playerId : playerLanguages.keySet()) {
            if (disabledPlayers.contains(playerId)) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            }
        }

        return onlinePlayers;
    }

    /**
     * Clear all player data
     */
    public void clearAll() {
        playerLanguages.clear();
        disabledPlayers.clear();
        lastActivity.clear();
        languageUsage.clear();
        debugLog("All player data cleared");
    }

    /**
     * Remove a specific player's data
     */
    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        String language = playerLanguages.remove(playerId);
        disabledPlayers.remove(playerId);
        lastActivity.remove(playerId);

        // Update language usage statistics
        if (language != null) {
            languageUsage.computeIfPresent(language, (k, v) -> Math.max(0, v - 1));
        }

        debugLog("Removed player data for: " + playerId);
    }

    /**
     * Get count of players with active translations
     * This fixes the method name issue in your main class
     */
    public int getActiveTranslationCount() {
        return (int) playerLanguages.keySet().stream()
                .filter(playerId -> !disabledPlayers.contains(playerId))
                .count();
    }

    /**
     * Get total number of players (including disabled)
     */
    public int getActivePlayersCount() {
        return playerLanguages.size();
    }

    /**
     * Get count of disabled players
     */
    public int getDisabledPlayersCount() {
        return disabledPlayers.size();
    }

    /**
     * Check if player has ever used translation
     */
    public boolean hasPlayerUsedTranslation(UUID playerId) {
        return playerId != null && (playerLanguages.containsKey(playerId) || disabledPlayers.contains(playerId));
    }

    /**
     * Update player activity timestamp
     */
    private void updateActivity(UUID playerId) {
        if (playerId != null) {
            lastActivity.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Get player's last activity time
     */
    public Long getLastActivity(UUID playerId) {
        return lastActivity.get(playerId);
    }

    /**
     * Clean up inactive players (haven't been seen for specified minutes)
     */
    public int cleanupInactivePlayers(int inactiveMinutes) {
        long cutoffTime = System.currentTimeMillis() - (inactiveMinutes * 60 * 1000L);
        int cleaned = 0;

        // Create list of players to remove to avoid concurrent modification
        Set<UUID> toRemove = new HashSet<>();

        for (Map.Entry<UUID, Long> entry : lastActivity.entrySet()) {
            UUID playerId = entry.getKey();
            Long lastSeen = entry.getValue();

            // Check if player is offline and inactive
            Player player = Bukkit.getPlayer(playerId);
            if ((player == null || !player.isOnline()) &&
                    (lastSeen == null || lastSeen < cutoffTime)) {
                toRemove.add(playerId);
            }
        }

        // Remove inactive players
        for (UUID playerId : toRemove) {
            removePlayer(playerId);
            cleaned++;
        }

        if (cleaned > 0) {
            debugLog("Cleaned up " + cleaned + " inactive players");
        }

        return cleaned;
    }

    /**
     * Get language usage statistics
     */
    public Map<String, Integer> getLanguageUsageStats() {
        return new HashMap<>(languageUsage);
    }

    /**
     * Get most popular language
     */
    public String getMostPopularLanguage() {
        return languageUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("en");
    }

    /**
     * Increment total translation counter
     */
    public void incrementTranslationCount() {
        totalTranslations++;
    }

    /**
     * Get total translation count
     */
    public long getTotalTranslations() {
        return totalTranslations;
    }

    /**
     * Get comprehensive statistics
     */
    public PlayerStatistics getStatistics() {
        return new PlayerStatistics(
                getActiveTranslationCount(),
                getDisabledPlayersCount(),
                getTotalTranslations(),
                getMostPopularLanguage(),
                languageUsage.size()
        );
    }

    /**
     * Debug logging helper
     */
    private void debugLog(String message) {
        if (plugin != null && plugin.getConfigManager() != null) {
            plugin.getConfigManager().debugLog("[PlayerManager] " + message);
        }
    }

    /**
     * Statistics data class
     */
    public static class PlayerStatistics {
        public final int activeTranslations;
        public final int disabledPlayers;
        public final long totalTranslations;
        public final String mostPopularLanguage;
        public final int uniqueLanguages;

        public PlayerStatistics(int activeTranslations, int disabledPlayers,
                                long totalTranslations, String mostPopularLanguage,
                                int uniqueLanguages) {
            this.activeTranslations = activeTranslations;
            this.disabledPlayers = disabledPlayers;
            this.totalTranslations = totalTranslations;
            this.mostPopularLanguage = mostPopularLanguage;
            this.uniqueLanguages = uniqueLanguages;
        }

        @Override
        public String toString() {
            return String.format(
                    "PlayerStatistics{active=%d, disabled=%d, total=%d, popular='%s', languages=%d}",
                    activeTranslations, disabledPlayers, totalTranslations,
                    mostPopularLanguage, uniqueLanguages
            );
        }
    }
}