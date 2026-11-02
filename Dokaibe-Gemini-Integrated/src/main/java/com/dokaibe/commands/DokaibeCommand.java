
package com.dokaibe.commands;

import com.dokaibe.DokaibePlugin;
import com.dokaibe.permissions.Authority;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DokaibeCommand implements CommandExecutor {

    private final DokaibePlugin plugin;
    private final Authority auth;

    public DokaibeCommand(DokaibePlugin plugin) {
        this.plugin = plugin;
        this.auth = new Authority(plugin);
    }

    private void tell(CommandSender s, String msg) {
        if (s instanceof Player p && auth.isFounder(p))
            s.sendMessage(plugin.identity().owner(msg));
        else if (s instanceof Player p && auth.isAdmin(p))
            s.sendMessage(plugin.identity().admin(msg));
        else if (s instanceof Player)
            s.sendMessage(plugin.identity().player(msg));
        else
            s.sendMessage(plugin.identity().system(msg));
    }

    private void help(CommandSender sender) {

        tell(sender, "");
        tell(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        tell(sender, "<gradient:#00E5FF:#7B61FF><bold>          DOKAIBE AI</bold></gradient>");
        tell(sender, "<gray>          Command Center</gray>");
        tell(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        tell(sender, "<aqua><bold>GENERAL COMMANDS</bold></aqua>");
        tell(sender, "<white>/dokaibe help</white> <dark_gray>-</dark_gray> <gray>Show this menu</gray>");
        tell(sender, "<white>/dokaibe status</white> <dark_gray>-</dark_gray> <gray>Check system status</gray>");
        tell(sender, "<white>/dokaibe whoami</white> <dark_gray>-</dark_gray> <gray>View your identity</gray>");
        tell(sender, "<white>/dokaibe players</white> <dark_gray>-</dark_gray> <gray>List online players</gray>");

        tell(sender, "");
        tell(sender, "<gold><bold>OWNER COMMANDS</bold></gold>");
        tell(sender, "<white>/dokaibe on</white> <dark_gray>-</dark_gray> <gray>Activate Dokaibe</gray>");
        tell(sender, "<white>/dokaibe off</white> <dark_gray>-</dark_gray> <gray>Deactivate Dokaibe</gray>");
        tell(sender, "<white>/dokaibe report</white> <dark_gray>-</dark_gray> <gray>Send server report</gray>");

        tell(sender, "");
        tell(sender, "<red><bold>FOUNDER COMMANDS</bold></red>");
        tell(sender, "<white>/dokaibe reload</white> <dark_gray>-</dark_gray> <gray>Reload configuration</gray>");
        tell(sender, "<white>/dokaibe admin add <player></white> <dark_gray>-</dark_gray> <gray>Add admin</gray>");
        tell(sender, "<white>/dokaibe admin remove <player></white> <dark_gray>-</dark_gray> <gray>Remove admin</gray>");

        tell(sender, "");
        tell(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        tell(sender, "<gray>Version: <white>" + plugin.getDescription().getVersion()
                + "</white> <dark_gray>|</dark_gray> Status: "
                + (plugin.isActive() ? "<green>ACTIVE" : "<red>OFFLINE")
                + "</gray>");
        tell(sender, "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        tell(sender, "");

    }

    private boolean founder(CommandSender s) {
        if (s instanceof Player p && auth.isFounder(p)) return true;
        tell(s, "<red>Only the configured founder UUID (Zeroxxxy) can change core settings.</red>");
        return false;
    }

    private boolean owner(CommandSender s) {
        if (s instanceof Player p && auth.isOwner(p)) return true;
        tell(s, "<red>Founder/owner authorization required.</red>");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "status" ->
                    tell(sender, "System: " +
                            (plugin.isActive() ? "<green>ACTIVE" : "<red>OFFLINE") +
                            "</red> | Players: " + Bukkit.getOnlinePlayers().size());

            case "on", "off" -> {
                if (!owner(sender)) return true;
                plugin.setActive(args[0].equalsIgnoreCase("on"));
                tell(sender, "Dokaibe state updated.");
            }

            case "whoami" -> {
                if (sender instanceof Player p) {
                    tell(sender, "Name: " + p.getName()
                            + " | UUID: " + p.getUniqueId()
                            + " | Founder: " + auth.isFounder(p)
                            + " | Admin: " + auth.isAdmin(p));
                } else {
                    tell(sender, "Console has no player UUID.");
                }
            }

            case "players" ->
                    tell(sender, "Online (" + Bukkit.getOnlinePlayers().size() + "): " +
                            String.join(", ", Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName).toList()));

            case "report" -> {
                if (!owner(sender)) return true;
                plugin.discord().send("Manual report requested by " + sender.getName()
                        + ". Players: " + Bukkit.getOnlinePlayers().size()
                        + ". Dokaibe: " + (plugin.isActive() ? "ACTIVE" : "OFFLINE"));
                tell(sender, "Report queued.");
            }

            case "reload" -> {
                if (!founder(sender)) return true;
                plugin.reloadConfig();
                tell(sender, "Configuration reloaded. Founder-only controls remain protected by the configured founder UUID.");
            }

            case "admin" -> {
                if (!founder(sender)) return true;

                if (args.length < 3 ||
                        !(args[1].equalsIgnoreCase("add") ||
                                args[1].equalsIgnoreCase("remove"))) {
                    tell(sender, "Usage: /dokaibe admin add|remove <player>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);

                List<String> admins = new ArrayList<>(
                        plugin.getConfig().getStringList("security.admin-uuids"));

                if (args[1].equalsIgnoreCase("add")) {
                    if (!admins.contains(target.getUniqueId().toString()))
                        admins.add(target.getUniqueId().toString());
                } else {
                    admins.remove(target.getUniqueId().toString());
                }

                plugin.getConfig().set("security.admin-uuids", admins);
                plugin.saveConfig();

                plugin.database().log(sender.getName(),
                        "ADMIN_LIST_CHANGE",
                        args[1] + " " + target.getUniqueId());

                tell(sender, "Admin list updated for UUID " + target.getUniqueId() + ".");
            }

            case "auth", "confirm", "cancel" ->
                    tell(sender, "This authentication/confirmation feature is not enabled in this starter build. Do not rely on it for privileged access.");

            default ->
                    tell(sender, "Unknown subcommand. Use /dokaibe help.");
        }

        return true;
    }
}
