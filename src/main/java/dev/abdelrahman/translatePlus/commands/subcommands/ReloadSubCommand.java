package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand {

    private final TranslatePlus plugin;

    public ReloadSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("translateplus.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        try {
            sender.sendMessage("§7Reloading TranslatePlus configuration...");

            // Reload configuration
            plugin.getConfigManager().reloadConfig();

            // Clear caches to apply new settings
            plugin.getTranslateService().clearCache();

            sender.sendMessage(plugin.getConfigManager().getMessage("reload"));

            // Validate API key after reload
            if (!plugin.getTranslateService().validateApiKey()) {
                sender.sendMessage("§cWarning: Google Translate API key is not set or invalid!");
                sender.sendMessage("§cTranslations will fail until API key is properly configured.");
            } else {
                sender.sendMessage("§aAPI key validation successful!");
            }

            // Show config summary if debug mode is enabled
            if (plugin.getConfigManager().isDebugModeEnabled()) {
                sender.sendMessage("§7=== Configuration Summary ===");
                sender.sendMessage("§7Default language: " + plugin.getConfigManager().getDefaultLanguage());
                sender.sendMessage("§7Cache size limit: " + plugin.getConfigManager().getCacheSize());
                sender.sendMessage("§7Rate limit: " + plugin.getConfigManager().getRateLimitPerMinute() + " per minute");
                sender.sendMessage("§7Max message length: " + plugin.getConfigManager().getMaxMessageLength());
                sender.sendMessage("§7Debug mode: " + (plugin.getConfigManager().isDebugModeEnabled() ? "§aON" : "§cOFF"));
                sender.sendMessage("§7Translate commands: " + (plugin.getConfigManager().isTranslateCommandsEnabled() ? "§aON" : "§cOFF"));
            }

            plugin.getConfigManager().debugLog("Configuration reloaded by " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage("§cError reloading configuration: " + e.getMessage());
            plugin.getLogger().severe("Configuration reload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}