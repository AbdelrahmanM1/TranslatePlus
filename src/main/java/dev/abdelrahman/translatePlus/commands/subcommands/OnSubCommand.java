package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OnSubCommand {

    private final TranslatePlus plugin;

    public OnSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return;
        }

        if (!sender.hasPermission("translateplus.use")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§eUsage: /translate on <language>");
            sender.sendMessage("§eExample: /translate on es");
            sender.sendMessage("§eUse /translate list to see available languages");
            return;
        }

        String targetLanguage = args[1].toLowerCase();

        if (!plugin.getTranslateService().isLanguageSupported(targetLanguage)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-language", "lang", targetLanguage));
            return;
        }

        if (!plugin.getTranslateService().validateApiKey()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-api-key"));
            return;
        }

        plugin.getPlayerManager().enableTranslation(player.getUniqueId(), targetLanguage);

        String languageName = plugin.getTranslateService().getSupportedLanguages().get(targetLanguage);
        sender.sendMessage(plugin.getConfigManager().getMessage("enabled", "lang", languageName + " (" + targetLanguage + ")"));
    }
}