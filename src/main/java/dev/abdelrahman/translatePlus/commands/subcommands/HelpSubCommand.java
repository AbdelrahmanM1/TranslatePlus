package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import org.bukkit.command.CommandSender;

public class HelpSubCommand {

    private final TranslatePlus plugin;

    public HelpSubCommand(TranslatePlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("§6=== TranslatePlus Help ===");
        sender.sendMessage("§e/translate on <language> §7- Enable translation to specified language");
        sender.sendMessage("§e/translate off §7- Disable translation");
        sender.sendMessage("§e/translate status §7- Show your current translation status");
        sender.sendMessage("§e/translate list §7- Show all supported languages");
        sender.sendMessage("§e/translate help §7- Show this help message");

        if (sender.hasPermission("translateplus.admin")) {
            sender.sendMessage("§c=== Admin Commands ===");
            sender.sendMessage("§e/translate reload §7- Reload plugin configuration");
            sender.sendMessage("§e/translate setdefault <language> §7- Set server default language");
        }

        sender.sendMessage("§7Examples:");
        sender.sendMessage("§7  /translate on es §8(Enable Spanish translation)");
        sender.sendMessage("§7  /translate on zh §8(Enable Chinese translation)");
        sender.sendMessage("§7Use §e/translate list §7to see all language codes");
    }
}