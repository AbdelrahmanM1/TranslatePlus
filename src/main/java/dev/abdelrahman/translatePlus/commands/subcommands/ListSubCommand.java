package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import dev.abdelrahman.translatePlus.utils.LanguageUtil;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ListSubCommand {

    private final TranslatePlus plugin;

    public ListSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("translateplus.use")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        sender.sendMessage("§6=== Supported Languages ===");

        Map<String, String> languages = plugin.getTranslateService().getSupportedLanguages();
        List<String> rtlLanguages = plugin.getConfigManager().getRtlLanguages();
        List<String> verboseLanguages = plugin.getConfigManager().getVerboseLanguages();

        if (languages == null || languages.isEmpty()) {
            sender.sendMessage("§cNo languages available - check API configuration!");
            return;
        }

        // Sort languages alphabetically by name
        List<Map.Entry<String, String>> sortedLanguages = new ArrayList<>(languages.entrySet());
        sortedLanguages.sort(Map.Entry.comparingByValue());

        StringBuilder message = new StringBuilder();
        int count = 0;

        for (Map.Entry<String, String> entry : sortedLanguages) {
            if (count > 0 && count % 3 == 0) {
                sender.sendMessage(message.toString());
                message = new StringBuilder();
            }

            String langCode = entry.getKey();
            String langName = entry.getValue();

            // Add special indicators
            String indicators = "";
            if (rtlLanguages != null && rtlLanguages.contains(langCode)) {
                indicators += "§b[RTL]";
            }
            if (verboseLanguages != null && verboseLanguages.contains(langCode)) {
                indicators += "§d[V]";
            }

            message.append(String.format("§e%s §7(%s)%s ", langName, langCode, indicators));
            count++;
        }

        if (message.length() > 0) {
            sender.sendMessage(message.toString());
        }

        sender.sendMessage("§7Total: " + languages.size() + " languages");
        sender.sendMessage("§7Default: " + plugin.getConfigManager().getDefaultLanguage());

        if ((rtlLanguages != null && rtlLanguages.size() > 0) ||
                (verboseLanguages != null && verboseLanguages.size() > 0)) {
            sender.sendMessage("§7Indicators: §b[RTL]§7=Right-to-Left, §d[V]§7=Verbose");
        }

        sender.sendMessage("§7Usage: §e/translate on <code> §7(e.g., /translate on es)");
    }
}