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
            // Reload configuration
            plugin.getConfigManager().reloadConfig();

            // Clear caches
            plugin.getTranslateService().clearCache();

            sender.sendMessage(plugin.getConfigManager().getMessage("reload"));

            // Validate API key after reload
            if (!plugin.getTranslateService().validateApiKey()) {
                sender.sendMessage("§cWarning: Google Translate API key is not set or invalid!");
            }

            // Show config summary if debug mode is enabled
            if (plugin.getConfigManager().isDebugModeEnabled()) {
                sender.sendMessage("§7Config Summary:");
                sender.sendMessage("§7- Default language: " + plugin.getConfigManager().getDefaultLanguage());
                sender.sendMessage("§7- Cache size limit: " + plugin.getConfigManager().getCacheSize());
                sender.sendMessage("§7- Rate limit: " + plugin.getConfigManager().getRateLimitPerMinute() + "/min");
                sender.sendMessage("§7- Debug mode: " + plugin.getConfigManager().isDebugModeEnabled());
            }

            plugin.getConfigManager().debugLog("Config reloaded by " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage("§cError reloading config: " + e.getMessage());
            plugin.getLogger().severe("Config reload failed: " + e.getMessage());
        }
    }
}
