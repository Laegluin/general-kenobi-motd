package de.robinmirow.general_kenobi_motd;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class Plugin extends JavaPlugin implements Listener {
    /**
     * The maximum allowed response time for a greeting, in seconds.
     */
    static final int MAX_RESPONSE_TIME = 10;

    @Override
    public void onEnable() {
        ConcurrentHashMap<UUID, GreetingState> pendingGreetings = new ConcurrentHashMap<>();
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, pendingGreetings), this);
        this.getServer().getPluginManager().registerEvents(new ChatListener(pendingGreetings), this);
    }
}

class PlayerJoinListener implements Listener {
    Plugin plugin;
    ConcurrentHashMap<UUID, GreetingState> pendingGreetings;
    long nextGreetingId;

    PlayerJoinListener(Plugin plugin, ConcurrentHashMap<UUID, GreetingState> pendingGreetings) {
        this.plugin = plugin;
        this.pendingGreetings = pendingGreetings;
        this.nextGreetingId = 0;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        this.plugin.getServer().broadcastMessage(ChatColor.GREEN + "[General Kenobi] Hello there!");

        UUID playerId = e.getPlayer().getUniqueId();
        GreetingState greetingState = new GreetingState(this.nextGreetingId(), 0);
        this.pendingGreetings.put(playerId, greetingState);

        this.scheduleResponseCheck(playerId, greetingState.id);
    }

    private long nextGreetingId() {
        long id = this.nextGreetingId;
        this.nextGreetingId++;
        return id;
    }

    private void scheduleResponseCheck(UUID playerId, long greetingId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerJoinListener.this.onResponseTimeElapsed(playerId, greetingId);
            }
        }.runTaskLater(this.plugin, Plugin.MAX_RESPONSE_TIME * 20);
    }

    private void onResponseTimeElapsed(UUID playerId, long greetingId) {
        GreetingState greetingState = this.pendingGreetings.computeIfPresent(playerId,
                (id, state) -> new GreetingState(state.id, state.numMisses + 1));

        if (greetingState == null) {
            return;
        }

        if (greetingId != greetingState.id) {
            return;
        }

        Server server = this.plugin.getServer();
        Player player = server.getPlayer(playerId);

        // player is no longer on the server
        if (player == null) {
            this.pendingGreetings.remove(playerId);
            return;
        }

        switch (greetingState.numMisses) {
            case 0: {
                this.pendingGreetings.remove(playerId);
                break;
            }
            case 1: {
                server.broadcastMessage(ChatColor.GREEN + "[General Kenobi] Oh, I don't think so!");

                int duration = 20 * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 1, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, duration, 8, true));

                this.scheduleResponseCheck(playerId, greetingId);
                break;
            }
            default: {
                this.pendingGreetings.remove(playerId);
                server.broadcastMessage(ChatColor.GREEN + "[General Kenobi] So uncivilized.");
                player.kickPlayer("So uncivilized.");
                break;
            }
        }
    }
}

class ChatListener implements Listener {
    ConcurrentHashMap<UUID, GreetingState> pendingGreetings;

    ChatListener(ConcurrentHashMap<UUID, GreetingState> pendingGreetings) {
        this.pendingGreetings = pendingGreetings;
    }

    @EventHandler
    public void onChatMessage(AsyncPlayerChatEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();

        if (this.pendingGreetings.containsKey(playerId)) {
            if (e.getMessage().toLowerCase().contains("general kenobi")) {
                this.pendingGreetings.remove(playerId);
            }
        }
    }
}

class GreetingState {
    public long id;
    public int numMisses;

    public GreetingState(long id, int numMisses) {
        this.id = id;
        this.numMisses = numMisses;
    }
}
