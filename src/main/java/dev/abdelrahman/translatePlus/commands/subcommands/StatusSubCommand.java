package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatusSubCommand {

    private final TranslatePlus plugin;

    public StatusSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return;
        }

        if (!sender.hasPermission("translateplus.use")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        Player player = (Player) sender;

        boolean enabled = plugin.getPlayerManager().isTranslationEnabled(player.getUniqueId());
        String status = enabled ? "§aENABLED" : "§cDISABLED";
        String language = "none";

        if (enabled) {
            String langCode = plugin.getPlayerManager().getPlayerLanguage(player.getUniqueId());
            String languageName = LanguageUtil.getLanguageName(langCode,
                    plugin.getTranslateService().getSupportedLanguages());
            language = languageName + " (" + langCode + ")";
        }

        sender.sendMessage(plugin.getConfigManager().getMessage("status", new String[][]{
                {"status", status},
                {"lang", language}
        }));

        // Show additional debug info if debug mode is enabled
        if (plugin.getConfigManager().isDebugModeEnabled() && sender.hasPermission("translateplus.admin")) {
            sender.sendMessage("§7=== Debug Information ===");
            sender.sendMessage("§7Plugin fully loaded: " + plugin.isFullyLoaded());
            sender.sendMessage("§7Active players with translation: " + plugin.getPlayerManager().getActiveTranslationCount());
            sender.sendMessage("§7Translation cache size: " + plugin.getTranslateService().getCacheSize());
            sender.sendMessage("§7API key configured: " + plugin.getConfigManager().isApiKeyConfigured());
            sender.sendMessage("§7Service health: " + plugin.getTranslateService().isHealthy());
        }
    }
}
