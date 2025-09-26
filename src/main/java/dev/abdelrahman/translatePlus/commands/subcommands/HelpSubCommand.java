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
        sender.sendMessage("§e/translate on <language> §7- Enable translation");
        sender.sendMessage("§e/translate off §7- Disable translation");
        sender.sendMessage("§e/translate status §7- Show your translation status");
        sender.sendMessage("§e/translate list §7- Show supported languages");
        sender.sendMessage("§e/translate help §7- Show this help");

        if (sender.hasPermission("translateplus.admin")) {
            sender.sendMessage("§c=== Admin Commands ===");
            sender.sendMessage("§e/translate reload §7- Reload config");
            sender.sendMessage("§e/translate setdefault <language> §7- Set default language");
        }
    }
}