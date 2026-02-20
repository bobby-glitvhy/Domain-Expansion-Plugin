package me.yourname.domain;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DomainExpansionPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int RADIUS = 8;
    private static final int DURATION = 200; // 10 seconds (ticks)
    private static final int COOLDOWN = 60;  // 60 seconds

    private boolean domainActive = false;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final List<Block> placedBarriers = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("givedomain").setExecutor(this);
        getLogger().info("DomainExpansion enabled.");
    }

    @Override
    public void onDisable() {
        cleanupDomain();
    }

    // ----------------------------
    // COMMAND: /givedomain <player>
    // ----------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length != 1) {
            sender.sendMessage("Usage: /givedomain <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Domain Expansion");
        item.setItemMeta(meta);

        target.getInventory().addItem(item);
        sender.sendMessage("Gave Domain Expansion to " + target.getName());
        return true;
    }

    // ----------------------------
    // RIGHT CLICK ACTIVATION
    // ----------------------------
    @EventHandler
    public void onUse(PlayerInteractEvent event) {

        if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta()) return;
        if (!ChatColor.stripColor(item.getItemMeta().getDisplayName())
                .equalsIgnoreCase("Domain Expansion")) return;

        if (domainActive) {
            player.sendMessage(ChatColor.RED + "A domain is already active!");
            return;
        }

        // Cooldown check
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                player.sendMessage(ChatColor.RED + "Cooldown: " + timeLeft + "s");
                return;
            }
        }

        activateDomain(player);
        cooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (COOLDOWN * 1000));
    }

    // ----------------------------
    // DOMAIN LOGIC
    // ----------------------------
    private void activateDomain(Player caster) {

        domainActive = true;
        Location center = caster.getLocation();
        World world = center.getWorld();

        caster.sendMessage(ChatColor.DARK_PURPLE + "Domain Expansion Activated!");

        // Buff caster
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, DURATION, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DURATION, 1));

        // Create sphere wall
        placedBarriers.clear();

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {

                    double distance = x*x + y*y + z*z;

                    if (distance <= RADIUS * RADIUS &&
                            distance >= (RADIUS - 1) * (RADIUS - 1)) {

                        Block block = world.getBlockAt(
                                center.clone().add(x, y, z));

                        if (block.getType() == Material.AIR) {
                            placedBarriers.add(block);
                            block.setType(Material.BARRIER);
                        }
                    }
                }
            }
        }

        // Domain tick task
        new BukkitRunnable() {
            int time = 0;

            @Override
            public void run() {

                if (time >= DURATION) {
                    cleanupDomain();
                    cancel();
                    return;
                }

                for (Player p : world.getPlayers()) {
                    if (!p.equals(caster)
                            && p.getLocation().distance(center) <= RADIUS) {

                        p.damage(3.0, caster);
                        p.addPotionEffect(
                                new PotionEffect(
                                        PotionEffectType.SLOWNESS,
                                        40,
                                        2
                                )
                        );
                    }
                }

                // Safe particle for 1.21.11
                world.spawnParticle(
                        Particle.PORTAL,
                        center,
                        100,
                        4, 2, 4,
                        0.1
                );

                time += 20;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ----------------------------
    // CLEANUP
    // ----------------------------
    private void cleanupDomain() {

        for (Block b : placedBarriers) {
            if (b.getType() == Material.BARRIER) {
                b.setType(Material.AIR);
            }
        }

        placedBarriers.clear();
        domainActive = false;
    }
}
