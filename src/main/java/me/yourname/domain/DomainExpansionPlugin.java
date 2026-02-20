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

    private final int RADIUS = 8;
    private final int DURATION = 200; // 10 seconds
    private final int COOLDOWN = 60; // seconds

    private boolean domainActive = false;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("givedomain").setExecutor(this);
    }

    // Command to give item
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

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
        sender.sendMessage("Given Domain Expansion to " + target.getName());
        return true;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta()) return;
        if (!ChatColor.stripColor(item.getItemMeta().getDisplayName())
                .equals("Domain Expansion")) return;

        if (domainActive) {
            player.sendMessage(ChatColor.RED + "A domain is already active!");
            return;
        }

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                player.sendMessage(ChatColor.RED + "Cooldown: " + timeLeft + "s");
                return;
            }
        }

        activateDomain(player);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (COOLDOWN * 1000));
    }

    private void activateDomain(Player caster) {

        domainActive = true;
        Location center = caster.getLocation();
        World world = center.getWorld();
        List<Block> barrierBlocks = new ArrayList<>();

        caster.sendMessage(ChatColor.DARK_PURPLE + "Domain Expansion Activated!");

        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, DURATION, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DURATION, 1));

        // Create barrier sphere
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {

                    if (x*x + y*y + z*z >= RADIUS*RADIUS - 2 &&
                        x*x + y*y + z*z <= RADIUS*RADIUS + 2) {

                        Block block = world.getBlockAt(center.clone().add(x, y, z));
                        if (block.getType() == Material.AIR) {
                            barrierBlocks.add(block);
                            block.setType(Material.BARRIER);
                        }
                    }
                }
            }
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {

                if (ticks >= DURATION) {
                    for (Block b : barrierBlocks) {
                        b.setType(Material.AIR);
                    }
                    domainActive = false;
                    cancel();
                    return;
                }

                for (Player p : world.getPlayers()) {
                    if (!p.equals(caster) &&
                        p.getLocation().distance(center) <= RADIUS) {

                        p.damage(3.0, caster);
                        p.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, 2));
                    }
                }

                world.spawnParticle(Particle.DRAGON_BREATH, center, 100, 4, 2, 4);

                ticks += 20;
            }
        }.runTaskTimer(this, 0L, 20L);
    }
}
