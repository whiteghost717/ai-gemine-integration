package com.dokaibe.permissions;

import com.dokaibe.DokaibePlugin;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class Authority {
    private final DokaibePlugin plugin;
    public Authority(DokaibePlugin plugin) { this.plugin = plugin; }
    public boolean isFounder(Player p) {
        String raw = plugin.getConfig().getString("security.founder-uuid", "");
        try { return !raw.isBlank() && UUID.fromString(raw).equals(p.getUniqueId()); }
        catch (IllegalArgumentException ex) { return false; }
    }
    public boolean isOwner(Player p) {
        if (isFounder(p)) return true;
        return plugin.getConfig().getStringList("security.owner-uuids").contains(p.getUniqueId().toString());
    }
    public boolean isAdmin(Player p) {
        return isOwner(p) || plugin.getConfig().getStringList("security.admin-uuids").contains(p.getUniqueId().toString());
    }
    public boolean canChangeCore(Player p) { return isFounder(p); }
}
