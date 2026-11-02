package com.dokaibe.events;

import com.dokaibe.DokaibePlugin;
import com.dokaibe.permissions.Authority;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public final class PlayerEvents implements Listener {
    private final DokaibePlugin plugin; private final Authority authority;
    public PlayerEvents(DokaibePlugin plugin) { this.plugin = plugin; this.authority = new Authority(plugin); }

    @EventHandler public void onChat(AsyncChatEvent event) {
        if (!plugin.isActive()) return;

        String text = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        Player player = event.getPlayer();

        // @ is the Gemini AI trigger. It works independently of the legacy trigger below.
        if (text.startsWith("@")) {
            event.setCancelled(true);
            String query = text.substring(1).trim();
            plugin.geminiChat().ask(player.getUniqueId(), player.getName(), query);
            return;
        }

        // Keep the original local Dokaibe shortcuts for backward compatibility.
        String trigger = plugin.getConfig().getString(
                "chat.legacy-trigger",
                plugin.getConfig().getString("chat.trigger", "Dokaibe")
        );
        if (trigger == null || trigger.isBlank()
                || !text.regionMatches(true, 0, trigger, 0, trigger.length())) return;

        event.setCancelled(true);
        String query = text.substring(trigger.length())
                .replaceFirst("^[, :]+", "").trim();
        Bukkit.getScheduler().runTask(plugin, () -> respond(player, query));
    }

    private void respond(Player player, String query) {
        if (query.isBlank() || query.equalsIgnoreCase("hello") || query.equalsIgnoreCase("hi")) {
            send(player, "Hello " + player.getName() + "! I am Dokaibe, the server's AI assistant."); return;
        }
        String q = query.toLowerCase();
        if (q.contains("online") || q.contains("players")) {
            send(player, "There are currently " + Bukkit.getOnlinePlayers().size() + " players online: " + String.join(", ", Bukkit.getOnlinePlayers().stream().map(Player::getName).toList())); return;
        }
        if (q.contains("rule")) {
            send(player, "Server rules:");
            for (String rule : plugin.getConfig().getStringList("chat.rules")) send(player, "• " + rule); return;
        }
        if (q.contains("help")) { send(player, plugin.getConfig().getString("chat.help-message", "Ask me about server rules or online players.")); return; }
        if (q.contains("founder") || q.contains("creator")) {
            send(player, "My founder is Zeroxxxy, Supreme Founder."); return;
        }
        send(player, "I don't have a local answer for that yet. Try asking about online players, server rules, or help.");
    }

    private void send(Player player, String message) {
        if (authority.isFounder(player)) player.sendMessage(plugin.identity().owner(message));
        else if (authority.isAdmin(player)) player.sendMessage(plugin.identity().admin(message));
        else player.sendMessage(plugin.identity().player(message));
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("monitoring.report-joins-leaves", true)) {
            plugin.database().log(e.getPlayer().getUniqueId().toString(), "JOIN", e.getPlayer().getName());
            plugin.discord().send(e.getPlayer().getName() + " joined. Online: " + Bukkit.getOnlinePlayers().size());
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (plugin.getConfig().getBoolean("monitoring.report-joins-leaves", true)) {
            plugin.database().log(e.getPlayer().getUniqueId().toString(), "QUIT", e.getPlayer().getName());
            plugin.discord().send(e.getPlayer().getName() + " left.");
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        if (plugin.getConfig().getBoolean("monitoring.report-deaths", true)) plugin.discord().send(e.getEntity().getName() + " died: " + e.getDeathMessage());
    }
    @EventHandler public void onRedstone(BlockRedstoneEvent e) {
        if (!plugin.getConfig().getBoolean("activation.redstone.enabled", false)) return;
        var c = plugin.getConfig(); var b = e.getBlock();
        if (!b.getWorld().getName().equals(c.getString("activation.redstone.world", "world")) || b.getX() != c.getInt("activation.redstone.x") || b.getY() != c.getInt("activation.redstone.y") || b.getZ() != c.getInt("activation.redstone.z")) return;
        boolean powered = e.getNewCurrent() > 0;
        boolean activateWhenPowered = c.getBoolean("activation.redstone.powered-state", true);
        plugin.setActive(powered == activateWhenPowered);
    }
}
