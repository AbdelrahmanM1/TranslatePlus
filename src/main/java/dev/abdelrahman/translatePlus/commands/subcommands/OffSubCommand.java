package dev.abdelrahman.translatePlus.commands.subcommands;

import dev.abdelrahman.translatePlus.TranslatePlus;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OffSubCommand {

    private final TranslatePlus plugin;

    public OffSubCommand(TranslatePlus plugin) {
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

        if (!plugin.getPlayerManager().isTranslationEnabled(player.getUniqueId())) {
            sender.sendMessage(plugin.getConfigManager().getMessage("already-disabled"));
            return;
        }

        plugin.getPlayerManager().disableTranslation(player.getUniqueId());
        sender.sendMessage(plugin.getConfigManager().getMessage("disabled"));

        plugin.getConfigManager().debugLog("Player " + player.getName() + " disabled translation");
    }
}
