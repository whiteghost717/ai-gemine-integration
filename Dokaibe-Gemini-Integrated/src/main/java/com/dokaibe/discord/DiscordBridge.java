package com.dokaibe.discord;

import com.dokaibe.DokaibePlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;

/** Minimal token-authenticated local bridge. Put it behind TLS/reverse proxy if exposed beyond localhost. */
public final class DiscordBridge {
    private final DokaibePlugin plugin; private HttpServer server; private java.util.concurrent.ExecutorService executor;
    public DiscordBridge(DokaibePlugin plugin) { this.plugin = plugin; }
    public void start() {
        if (!plugin.getConfig().getBoolean("discord.bridge-enabled", false)) return;
        String token = plugin.getConfig().getString("discord.bridge-token", "");
        if (token == null || token.length() < 32) { plugin.getLogger().warning("Discord bridge disabled: bridge-token must be at least 32 characters."); return; }
        try {
            server = HttpServer.create(new InetSocketAddress(plugin.getConfig().getString("discord.bridge-host", "127.0.0.1"), plugin.getConfig().getInt("discord.bridge-port", 8765)), 16);
            server.createContext("/", this::handle); executor = java.util.concurrent.Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "Dokaibe-Discord-Bridge"); t.setDaemon(true); return t; }); server.setExecutor(executor); server.start();
            plugin.getLogger().info("Discord bridge listening on " + server.getAddress());
        } catch (IOException e) { plugin.getLogger().severe("Could not start Discord bridge: " + e.getMessage()); }
    }
    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { reply(ex, 405, "method_not_allowed"); return; }
            String expected = plugin.getConfig().getString("discord.bridge-token", "");
            String supplied = ex.getRequestHeaders().getFirst("X-Dokaibe-Token");
            if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) { reply(ex, 401, "unauthorized"); return; }
            String discordId = ex.getRequestHeaders().getFirst("X-Discord-User");
            if (discordId == null) { reply(ex, 401, "missing_discord_identity"); return; }
            String path = ex.getRequestURI().getPath();
            boolean owner = plugin.getConfig().getStringList("discord.owner-user-ids").contains(discordId);
            boolean admin = owner || plugin.getConfig().getStringList("discord.admin-user-ids").contains(discordId);
            if (path.equals("/status")) { try { String status = Bukkit.getScheduler().callSyncMethod(plugin, () -> "active=" + plugin.isActive() + ";players=" + Bukkit.getOnlinePlayers().size()).get(3, java.util.concurrent.TimeUnit.SECONDS); reply(ex, 200, status); } catch (Exception timeout) { reply(ex, 503, "status_unavailable"); } return; }
            if (!owner) { reply(ex, 403, "owner_only"); return; }
            if (path.equals("/on") || path.equals("/off")) {
                boolean state = path.equals("/on"); Bukkit.getScheduler().runTask(plugin, () -> plugin.setActive(state));
                plugin.database().log("discord:" + discordId, "DOKAIBE_" + (state ? "ON" : "OFF"), "Discord bridge"); reply(ex, 202, "queued"); return;
            }
            reply(ex, 404, "not_found");
        } catch (Exception e) { reply(ex, 500, "internal_error"); }
    }
    private void reply(HttpExchange ex, int code, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8"); ex.sendResponseHeaders(code, bytes.length); ex.getResponseBody().write(bytes); ex.close(); }
    public void stop() { if (server != null) server.stop(1); if (executor != null) executor.shutdownNow(); }
}
