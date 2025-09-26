package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
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
        String language = enabled ? plugin.getPlayerManager().getPlayerLanguage(player.getUniqueId()) : "none";

        if (enabled) {
            String languageName = plugin.getTranslateService().getSupportedLanguages().get(language);
            language = languageName + " (" + language + ")";
        }

        sender.sendMessage(plugin.getConfigManager().getMessage("status", new String[][]{
                {"status", status},
                {"lang", language}
        }));

        // Show additional debug info if debug mode is enabled
        if (plugin.getConfigManager().isDebugModeEnabled() && sender.hasPermission("translateplus.admin")) {
            sender.sendMessage("§7Debug Info:");
            sender.sendMessage("§7- Active players: " + plugin.getPlayerManager().getActivePlayersCount());
            sender.sendMessage("§7- Cache size: " + plugin.getTranslateService().getCacheSize());
            sender.sendMessage("§7- API key valid: " + plugin.getTranslateService().validateApiKey());
        }
    }
}