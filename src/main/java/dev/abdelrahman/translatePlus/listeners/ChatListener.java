package dev.abdelrahman.translatePlus.listeners;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChatListener implements Listener {

    private final TranslatePlus plugin;
    private final ConfigManager configManager;

    // Rate limiting for players
    private final ConcurrentHashMap<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> messageCount = new ConcurrentHashMap<>();

    public ChatListener(TranslatePlus plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        Player sender = event.getPlayer();

        // Skip if message is empty or sender doesn't exist
        if (message == null || message.trim().isEmpty() || sender == null) {
            return;
        }

        // Validate message length using ConfigManager
        if (!configManager.isValidMessageLength(message)) {
            configManager.debugLog("Message from " + sender.getName() + " rejected due to invalid length: " + message.length());
            return;
        }

        // Check if message is blacklisted
        if (configManager.isBlacklisted(message)) {
            configManager.debugLog("Message from " + sender.getName() + " blacklisted: " + message);
            return;
        }

        // Rate limiting check
        UUID senderId = sender.getUniqueId();
        if (!checkRateLimit(senderId)) {
            sender.sendMessage(configManager.getMessage("rate-limit-exceeded"));
            return;
        }

        // Check if any online players have translation enabled
        boolean anyTranslationEnabled = false;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerManager().isTranslationEnabled(onlinePlayer.getUniqueId())) {
                anyTranslationEnabled = true;
                break;
            }
        }

        if (!anyTranslationEnabled) {
            configManager.debugLog("No players have translation enabled, skipping translation");
            return; // No one needs translation
        }

        // Truncate message if needed
        String processedMessage = configManager.truncateMessage(message);

        // Process translation for each player who has it enabled
        for (Player recipient : event.getRecipients().toArray(new Player[0])) { // Convert to array to avoid ConcurrentModificationException
            UUID recipientId = recipient.getUniqueId();

            if (plugin.getPlayerManager().isTranslationEnabled(recipientId)) {
                String targetLanguage = plugin.getPlayerManager().getPlayerLanguage(recipientId);

                // Don't translate if it's the same player speaking
                if (sender.getUniqueId().equals(recipientId)) {
                    configManager.debugLog("Skipping translation for sender: " + sender.getName());
                    continue;
                }

                configManager.debugLog("Translating message for " + recipient.getName() + " to " + targetLanguage);

                // Translate the message asynchronously
                plugin.getTranslateService().translateText(processedMessage, targetLanguage)
                        .thenAccept(translatedMessage -> {
                            // Send translated message to the player on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (recipient.isOnline()) {
                                    String translationSuffix = isRightToLeftLanguage(targetLanguage) ?
                                            " §8[مترجم]" : " §8[Translated]";

                                    String formattedMessage = String.format(event.getFormat(),
                                            sender.getDisplayName(), translatedMessage + translationSuffix);
                                    recipient.sendMessage(formattedMessage);

                                    configManager.debugLog("Sent translated message to " + recipient.getName());
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            configManager.debugLog("Translation failed for " + recipient.getName() + ": " + throwable.getMessage());

                            // Send error message to recipient
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (recipient.isOnline()) {
                                    recipient.sendMessage(configManager.getMessage("translation-error"));
                                }
                            });

                            plugin.getLogger().warning("Failed to translate message for " + recipient.getName() + ": " + throwable.getMessage());
                            return null;
                        });

                // Remove this recipient from the original event so they don't see the original message
                event.getRecipients().remove(recipient);
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Only translate commands if enabled in config
        if (!configManager.isTranslateCommandsEnabled()) {
            return;
        }

        String command = event.getMessage();
        Player sender = event.getPlayer();

        // Skip translation commands and other system commands
        if (command.toLowerCase().startsWith("/translate") ||
                command.toLowerCase().startsWith("/tr") ||
                command.toLowerCase().startsWith("/tp") ||
                command.toLowerCase().startsWith("/gamemode") ||
                command.toLowerCase().startsWith("/give") ||
                command.toLowerCase().startsWith("/op") ||
                command.toLowerCase().startsWith("/deop") ||
                command.toLowerCase().startsWith("/ban") ||
                command.toLowerCase().startsWith("/kick") ||
                command.toLowerCase().startsWith("/whitelist") ||
                command.toLowerCase().startsWith("/reload") ||
                command.toLowerCase().startsWith("/stop")) {
            return;
        }

        // Validate command length
        if (!configManager.isValidMessageLength(command)) {
            configManager.debugLog("Command from " + sender.getName() + " rejected due to invalid length: " + command.length());
            return;
        }

        // Check if command is blacklisted
        if (configManager.isBlacklisted(command)) {
            configManager.debugLog("Command from " + sender.getName() + " blacklisted: " + command);
            return;
        }

        // Rate limiting for commands
        UUID senderId = sender.getUniqueId();
        if (!checkRateLimit(senderId)) {
            sender.sendMessage(configManager.getMessage("rate-limit-exceeded"));
            return;
        }

        String processedCommand = configManager.truncateMessage(command);

        // Process similar to chat messages
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerId = onlinePlayer.getUniqueId();

            if (plugin.getPlayerManager().isTranslationEnabled(playerId) &&
                    !sender.getUniqueId().equals(playerId)) {

                String targetLanguage = plugin.getPlayerManager().getPlayerLanguage(playerId);

                configManager.debugLog("Translating command for " + onlinePlayer.getName() + " to " + targetLanguage);

                plugin.getTranslateService().translateText(processedCommand, targetLanguage)
                        .thenAccept(translatedCommand -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (onlinePlayer.isOnline()) {
                                    String translationSuffix = isRightToLeftLanguage(targetLanguage) ?
                                            " §8[مترجم]" : " §8[Translated]";

                                    onlinePlayer.sendMessage("§8[Command] " + sender.getDisplayName() +
                                            ": " + translatedCommand + translationSuffix);

                                    configManager.debugLog("Sent translated command to " + onlinePlayer.getName());
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            configManager.debugLog("Command translation failed for " + onlinePlayer.getName() + ": " + throwable.getMessage());

                            // Send error message to recipient
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (onlinePlayer.isOnline()) {
                                    onlinePlayer.sendMessage(configManager.getMessage("translation-error"));
                                }
                            });

                            plugin.getLogger().warning("Failed to translate command for " + onlinePlayer.getName() + ": " + throwable.getMessage());
                            return null;
                        });
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Clean up player data when they quit
        plugin.getPlayerManager().removePlayer(playerId);

        // Clean up rate limiting data
        lastMessageTime.remove(playerId);
        messageCount.remove(playerId);

        configManager.debugLog("Cleaned up data for player: " + event.getPlayer().getName());
    }

    /**
     * Check if player is within rate limits
     * @param playerId Player UUID
     * @return true if within limits, false if exceeded
     */
    private boolean checkRateLimit(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        int rateLimitPerMinute = configManager.getRateLimitPerMinute();

        // Clean up old entries
        cleanupRateLimitData(currentTime);

        Long lastTime = lastMessageTime.get(playerId);
        Integer count = messageCount.getOrDefault(playerId, 0);

        if (lastTime == null || currentTime - lastTime > TimeUnit.MINUTES.toMillis(1)) {
            // Reset counter for new minute
            lastMessageTime.put(playerId, currentTime);
            messageCount.put(playerId, 1);
            return true;
        } else if (count < rateLimitPerMinute) {
            // Within rate limit
            messageCount.put(playerId, count + 1);
            return true;
        } else {
            // Rate limit exceeded
            configManager.debugLog("Rate limit exceeded for player: " + playerId);
            return false;
        }
    }

    /**
     * Clean up old rate limiting data
     * @param currentTime Current timestamp
     */
    private void cleanupRateLimitData(long currentTime) {
        lastMessageTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TimeUnit.MINUTES.toMillis(5));

        // Remove corresponding message counts for cleaned up entries
        messageCount.entrySet().removeIf(entry ->
                !lastMessageTime.containsKey(entry.getKey()));
    }

    /**
     * Check if language is right-to-left
     * @param languageCode Language code
     * @return true if RTL language
     */
    private boolean isRightToLeftLanguage(String languageCode) {
        return configManager.isRightToLeftLanguage(languageCode);
    }
}