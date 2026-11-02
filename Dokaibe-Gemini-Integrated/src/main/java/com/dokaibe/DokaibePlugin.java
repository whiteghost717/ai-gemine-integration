package com.dokaibe;

import com.dokaibe.commands.DokaibeCommand;
import com.dokaibe.ai.GeminiChatService;
import com.dokaibe.database.DokaibeDatabase;
import com.dokaibe.discord.DiscordReporter;
import com.dokaibe.events.PlayerEvents;
import com.dokaibe.utils.ChatIdentity;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DokaibePlugin extends JavaPlugin {
    private final ExecutorService io = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Dokaibe-IO"); t.setDaemon(true); return t;
    });
    private DokaibeDatabase database;
    private DiscordReporter discord;
    private com.dokaibe.discord.DiscordBridge bridge;
    private ChatIdentity identity;
    private GeminiChatService geminiChat;
    private volatile boolean active;

    @Override public void onEnable() {
        saveDefaultConfig();
        this.identity = new ChatIdentity(this);
        this.geminiChat = new GeminiChatService(this);
        this.discord = new DiscordReporter(this, io);
        this.database = new DokaibeDatabase(this, io);
        this.bridge = new com.dokaibe.discord.DiscordBridge(this);
        this.bridge.start();
        getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);
        getCommand("dokaibe").setExecutor(new DokaibeCommand(this));
        this.active = getConfig().getBoolean("activation.start-enabled", false);
        if (getConfig().getBoolean("security.offline-mode-warning", true) && !Bukkit.getOnlineMode()) {
            getLogger().warning("OFFLINE MODE IS ENABLED. UUIDs are not cryptographically authenticated. Use an external login/authentication plugin and keep the founder UUID protected.");
        }
        if (active) announce(identity.system("✦ Dokaibe has awakened."));
        getLogger().info("Dokaibe AI enabled. Founder: Zeroxxxy — Supreme Founder.");
    }

    @Override public void onDisable() {
        if (active && identity != null) announce(identity.system("<italic>✦ Dokaibe has gone offline.</italic>"));
        active = false;
        if (discord != null) discord.send("Dokaibe has shut down.");
        if (bridge != null) bridge.stop();
        if (database != null) database.close();
        io.shutdown();
    }

    public boolean isActive() { return active; }
    public void setActive(boolean state) {
        if (active == state) return;
        active = state;
        String configured = getConfig().getString(state ? "identity.activation-message" : "identity.shutdown-message", state ? "Dokaibe has awakened." : "Dokaibe has gone offline.");
        announce(identity.system(configured));
        discord.send(state ? "Dokaibe activated." : "Dokaibe deactivated.");
        if (!state) getLogger().info("Dokaibe deactivated; normal gameplay remains enabled.");
        else getLogger().info("Dokaibe activated.");
    }
    public void announce(net.kyori.adventure.text.Component component) { Bukkit.broadcast(component); }
    public ChatIdentity identity() { return identity; }
    public GeminiChatService geminiChat() { return geminiChat; }

    /** Sends untrusted AI output as plain text, never as MiniMessage markup. */
    public void sendAiMessage(org.bukkit.entity.Player player, String message) {
        com.dokaibe.permissions.Authority authority = new com.dokaibe.permissions.Authority(this);
        if (authority.isFounder(player)) player.sendMessage(identity.ownerPlain(message));
        else if (authority.isAdmin(player)) player.sendMessage(identity.adminPlain(message));
        else player.sendMessage(identity.playerPlain(message));
    }
    public DiscordReporter discord() { return discord; }
    public DokaibeDatabase database() { return database; }
    public ExecutorService io() { return io; }
    public MiniMessage miniMessage() { return MiniMessage.miniMessage(); }
}
