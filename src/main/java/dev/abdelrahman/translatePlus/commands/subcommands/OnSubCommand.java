package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OnSubCommand {

    private final TranslatePlus plugin;

    public OnSubCommand(TranslatePlus plugin) {
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

        if (args.length < 2) {
            sender.sendMessage("§eUsage: /translate on <language>");
            sender.sendMessage("§eExample: /translate on es");
            sender.sendMessage("§eUse /translate list to see available languages");
            return;
        }

        String targetLanguage = LanguageUtil.normalizeLanguageCode(args[1]);

        // Validate language code format
        if (!LanguageUtil.isValidLanguageCode(targetLanguage)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-language", "lang", args[1]));
            return;
        }

        // Check if language is supported by the translation service
        if (!plugin.getTranslateService().isLanguageSupported(targetLanguage)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-language", "lang", targetLanguage));
            return;
        }

        // Validate API key
        if (!plugin.getTranslateService().validateApiKey()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-api-key"));
            return;
        }

        // Check if already enabled with same language
        if (plugin.getPlayerManager().isTranslationEnabled(player.getUniqueId())) {
            String currentLang = plugin.getPlayerManager().getPlayerLanguage(player.getUniqueId());
            if (targetLanguage.equals(currentLang)) {
                String languageName = LanguageUtil.getLanguageName(targetLanguage,
                        plugin.getTranslateService().getSupportedLanguages());
                sender.sendMessage(plugin.getConfigManager().getMessage("already-enabled",
                        "lang", languageName + " (" + targetLanguage + ")"));
                return;
            }
        }

        // Enable translation
        plugin.getPlayerManager().enableTranslation(player.getUniqueId(), targetLanguage);

        String languageName = LanguageUtil.getLanguageName(targetLanguage,
                plugin.getTranslateService().getSupportedLanguages());
        sender.sendMessage(plugin.getConfigManager().getMessage("enabled",
                "lang", languageName + " (" + targetLanguage + ")"));

        plugin.getConfigManager().debugLog("Player " + player.getName() +
                " enabled translation to " + targetLanguage);
    }
}