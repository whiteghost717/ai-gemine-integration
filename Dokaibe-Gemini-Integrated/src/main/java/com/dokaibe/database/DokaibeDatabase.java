package com.dokaibe.database;

import com.dokaibe.DokaibePlugin;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DokaibeDatabase {
    private final DokaibePlugin plugin; private final ExecutorService io; private volatile Connection connection; private final CountDownLatch ready = new CountDownLatch(1);
    public DokaibeDatabase(DokaibePlugin plugin, ExecutorService io) {
        this.plugin = plugin; this.io = io;
        io.submit(() -> {
            try {
                File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "dokaibe.db"));
                file.getParentFile().mkdirs(); connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("CREATE TABLE IF NOT EXISTS activity(id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT DEFAULT CURRENT_TIMESTAMP, actor TEXT, action TEXT, details TEXT)");
                }
            } catch (SQLException e) { plugin.getLogger().severe("SQLite initialization failed: " + e.getMessage()); } finally { ready.countDown(); }
        });
    }
    public void log(String actor, String action, String details) {
        io.submit(() -> { try { ready.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } if (connection == null) return; synchronized (this) { try (PreparedStatement ps = connection.prepareStatement("INSERT INTO activity(actor,action,details) VALUES(?,?,?)")) { ps.setString(1, actor); ps.setString(2, action); ps.setString(3, details); ps.executeUpdate(); } catch (SQLException e) { plugin.getLogger().warning("Database write failed: " + e.getMessage()); } } });
    }
    public void close() { io.submit(() -> { try { ready.await(10, TimeUnit.SECONDS); synchronized (this) { if (connection != null) connection.close(); } } catch (Exception ignored) {} }); }
}
