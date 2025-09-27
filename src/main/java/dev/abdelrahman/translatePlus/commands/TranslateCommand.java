package dev.abdelrahman.translatePlus.commands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.commands.subcommands.*;
import dev.abdelrahman.translatePlus.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TranslateCommand implements CommandExecutor, TabCompleter {

    private final TranslatePlus plugin;
    private final ConfigManager configManager;

    // Subcommand instances
    private final HelpSubCommand helpSubCommand;
    private final OnSubCommand onSubCommand;
    private final OffSubCommand offSubCommand;
    private final StatusSubCommand statusSubCommand;
    private final ListSubCommand listSubCommand;
    private final ReloadSubCommand reloadSubCommand;
    private final SetDefaultSubCommand setDefaultSubCommand;

    // Command cooldown to prevent spam
    private final Map<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 second cooldown

    // Supported subcommands with their required permissions
    private final Map<String, String> subCommandPermissions = Map.of(
            "on", "translateplus.use",
            "off", "translateplus.use",
            "status", "translateplus.use",
            "help", "translateplus.use",
            "list", "translateplus.use",
            "reload", "translateplus.admin",
            "setdefault", "translateplus.admin"
    );

    public TranslateCommand(TranslatePlus plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();

        // Initialize subcommands
        this.helpSubCommand = new HelpSubCommand(plugin);
        this.onSubCommand = new OnSubCommand(plugin);
        this.offSubCommand = new OffSubCommand(plugin);
        this.statusSubCommand = new StatusSubCommand(plugin);
        this.listSubCommand = new ListSubCommand(plugin);
        this.reloadSubCommand = new ReloadSubCommand(plugin);
        this.setDefaultSubCommand = new SetDefaultSubCommand(plugin);

        configManager.debugLog("TranslateCommand initialized with all subcommands");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for API key configuration first
        if (!configManager.isApiKeyConfigured()) {
            sender.sendMessage(configManager.getMessage("no-api-key"));
            configManager.debugLog("Command blocked: API key not configured");
            return true;
        }

        // Apply cooldown for players (not console)
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String playerId = player.getUniqueId().toString();

            if (isOnCooldown(playerId)) {
                // Don't send cooldown message to avoid spam, just ignore
                configManager.debugLog("Command ignored due to cooldown: " + player.getName());
                return true;
            }

            setCooldown(playerId);
        }

        // Show help if no arguments provided
        if (args.length == 0) {
            configManager.debugLog("No arguments provided, showing help");
            helpSubCommand.execute(sender, args);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        configManager.debugLog("Processing subcommand: " + subCommand + " from sender: " + sender.getName());

        // Check if subcommand exists
        if (!subCommandPermissions.containsKey(subCommand)) {
            sender.sendMessage(configManager.getMessage("usage"));
            configManager.debugLog("Unknown subcommand: " + subCommand);
            return true;
        }

        // Check permissions
        String requiredPermission = subCommandPermissions.get(subCommand);
        if (!sender.hasPermission(requiredPermission)) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            configManager.debugLog("Permission denied for " + sender.getName() + " on subcommand: " + subCommand);
            return true;
        }

        // Execute the appropriate subcommand
        try {
            switch (subCommand) {
                case "on":
                    onSubCommand.execute(sender, args);
                    break;
                case "off":
                    offSubCommand.execute(sender, args);
                    break;
                case "status":
                    statusSubCommand.execute(sender, args);
                    break;
                case "help":
                    helpSubCommand.execute(sender, args);
                    break;
                case "list":
                    listSubCommand.execute(sender, args);
                    break;
                case "reload":
                    reloadSubCommand.execute(sender, args);
                    break;
                case "setdefault":
                    setDefaultSubCommand.execute(sender, args);
                    break;
                default:
                    // This shouldn't happen due to earlier check, but just in case
                    sender.sendMessage(configManager.getMessage("usage"));
                    break;
            }

            configManager.debugLog("Successfully executed subcommand: " + subCommand);

        } catch (Exception e) {
            sender.sendMessage(configManager.getMessage("translation-error"));
            plugin.getLogger().severe("Error executing subcommand '" + subCommand + "': " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        try {
            if (args.length == 1) {
                // First argument - subcommand completion
                completions = getAvailableSubCommands(sender).stream()
                        .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());

                configManager.debugLog("Tab completion for subcommands: " + completions.size() + " matches");

            } else if (args.length == 2) {
                // Second argument completion based on subcommand
                String subCommand = args[0].toLowerCase();

                if ("on".equals(subCommand) || "setdefault".equals(subCommand)) {
                    // Language code completion
                    if (sender.hasPermission(subCommandPermissions.get(subCommand))) {
                        completions = getLanguageCompletions(args[1]);
                        configManager.debugLog("Tab completion for languages: " + completions.size() + " matches");
                    }
                }

            } else if (args.length == 3) {
                // Third argument completion (future expansion)
                // Currently no subcommands need a third argument
            }

        } catch (Exception e) {
            configManager.debugLog("Error in tab completion: " + e.getMessage());
            plugin.getLogger().warning("Tab completion error: " + e.getMessage());
        }

        return completions;
    }

    /**
     * Get available subcommands for the sender based on permissions
     */
    private List<String> getAvailableSubCommands(CommandSender sender) {
        List<String> available = new ArrayList<>();

        for (Map.Entry<String, String> entry : subCommandPermissions.entrySet()) {
            if (sender.hasPermission(entry.getValue())) {
                available.add(entry.getKey());
            }
        }

        return available;
    }

    /**
     * Get language code completions that match the partial input
     */
    private List<String> getLanguageCompletions(String partial) {
        List<String> completions = new ArrayList<>();

        try {
            Map<String, String> supportedLanguages = plugin.getTranslateService().getSupportedLanguages();

            if (supportedLanguages != null) {
                for (String langCode : supportedLanguages.keySet()) {
                    if (langCode.toLowerCase().startsWith(partial.toLowerCase())) {
                        completions.add(langCode);
                    }
                }
            }

            // If translate service doesn't have languages yet, provide common ones
            if (completions.isEmpty()) {
                List<String> commonLanguages = Arrays.asList(
                        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh",
                        "ar", "hi", "th", "vi", "tr", "pl", "nl", "sv", "da", "no"
                );

                for (String lang : commonLanguages) {
                    if (lang.toLowerCase().startsWith(partial.toLowerCase())) {
                        completions.add(lang);
                    }
                }
            }

        } catch (Exception e) {
            configManager.debugLog("Error getting language completions: " + e.getMessage());
        }

        return completions;
    }

    /**
     * Check if sender is on cooldown
     */
    private boolean isOnCooldown(String senderId) {
        Long lastUsed = commandCooldowns.get(senderId);
        if (lastUsed == null) {
            return false;
        }

        return System.currentTimeMillis() - lastUsed < COOLDOWN_MS;
    }

    /**
     * Set cooldown for sender
     */
    private void setCooldown(String senderId) {
        commandCooldowns.put(senderId, System.currentTimeMillis());

        // Clean up old cooldowns periodically
        if (commandCooldowns.size() > 100) {
            cleanupCooldowns();
        }
    }

    /**
     * Clean up expired cooldowns
     */
    private void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        commandCooldowns.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > COOLDOWN_MS * 10); // Keep for 10x cooldown time

        configManager.debugLog("Cleaned up command cooldowns, remaining: " + commandCooldowns.size());
    }

    /**
     * Get command statistics for debugging
     */
    public void logCommandStats() {
        if (configManager.isDebugModeEnabled()) {
            configManager.debugLog("Active command cooldowns: " + commandCooldowns.size());
            configManager.debugLog("Supported subcommands: " + subCommandPermissions.keySet());
        }
    }

    /**
     * Clear all cooldowns (useful for testing or admin commands)
     */
    public void clearCooldowns() {
        commandCooldowns.clear();
        configManager.debugLog("All command cooldowns cleared");
    }
}