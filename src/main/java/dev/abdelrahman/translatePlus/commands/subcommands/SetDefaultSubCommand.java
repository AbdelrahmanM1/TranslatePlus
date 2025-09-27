package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;
import org.bukkit.command.CommandSender;

public class SetDefaultSubCommand {

    private final TranslatePlus plugin;

    public SetDefaultSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("translateplus.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§eUsage: /translate setdefault <language>");
            sender.sendMessage("§eExample: /translate setdefault en");
            sender.sendMessage("§eUse /translate list to see available languages");
            return;
        }

        String language = LanguageUtil.normalizeLanguageCode(args[1]);

        // Validate language code format
        if (!LanguageUtil.isValidLanguageCode(language)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-language", "lang", args[1]));
            return;
        }

        // Check if language is supported
        if (!plugin.getTranslateService().isLanguageSupported(language)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-language", "lang", language));
            return;
        }

        // Set the default language
        plugin.getConfigManager().setDefaultLanguage(language);

        String languageName = LanguageUtil.getLanguageName(language,
                plugin.getTranslateService().getSupportedLanguages());
        sender.sendMessage(plugin.getConfigManager().getMessage("default-set",
                "lang", languageName + " (" + language + ")"));

        plugin.getConfigManager().debugLog("Default language changed to: " + language + " by " + sender.getName());
    }
}