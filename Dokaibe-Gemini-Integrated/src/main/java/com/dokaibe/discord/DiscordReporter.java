package com.dokaibe.discord;

import com.dokaibe.DokaibePlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

public final class DiscordReporter {
    private final DokaibePlugin plugin; private final ExecutorService io; private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public DiscordReporter(DokaibePlugin plugin, ExecutorService io) { this.plugin = plugin; this.io = io; }
    public void send(String message) {
        String url = plugin.getConfig().getString("discord.webhook-url", ""); if (url == null || url.isBlank()) return;
        io.submit(() -> {
            try {
                String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"[DOKAIBE AI] " + escaped + "\"}")).build();
                client.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) { plugin.getLogger().warning("Discord webhook failed: " + e.getMessage()); }
        });
    }
}
