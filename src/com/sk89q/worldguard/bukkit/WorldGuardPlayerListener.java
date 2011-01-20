// $Id$
/*
 * WorldGuard
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldguard.bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.*;
import com.sk89q.worldguard.blacklist.events.ItemUseBlacklistEvent;
import static com.sk89q.worldguard.bukkit.BukkitUtil.*;

/**
 * Handles all events thrown in relation to a Player
 */
public class WorldGuardPlayerListener extends PlayerListener {
    /**
     * Plugin.
     */
    private WorldGuardPlugin plugin;
    
    /**
     * Construct the object;
     * 
     * @param plugin
     */
    public WorldGuardPlayerListener(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player joins a server
     *
     * @param event Relevant event details
     */
    @Override
    public void onPlayerJoin(PlayerEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.fireSpreadDisableToggle) {
            player.sendMessage(ChatColor.YELLOW
                    + "Fire spread is currently globally disabled.");
        }

        if (plugin.inGroup(player, "wg-invincible")) {
            plugin.invinciblePlayers.add(player.getName());
        }

        if (plugin.inGroup(player, "wg-amphibious")) {
            plugin.amphibiousPlayers.add(player.getName());
        }
    }

    /**
     * Called when a player leaves a server
     *
     * @param event Relevant event details
     */
    @Override
    public void onPlayerQuit(PlayerEvent event) {
        Player player = event.getPlayer();
        plugin.invinciblePlayers.remove(player.getName());
        plugin.amphibiousPlayers.remove(player.getName());
        if (plugin.blacklist != null) {
            plugin.blacklist.forgetPlayer(plugin.wrapPlayer(player));
        }
    }
    
    /**
     * Called when a player uses an item
     * 
     * @param event Relevant event details
     */
    @Override
    public void onPlayerItem(PlayerItemEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        ItemStack item = event.getItem();
        
        if (plugin.useRegions && !event.isBlock() && block != null) {
            Vector pt = toVector(block.getRelative(event.getBlockFace()));
            LocalPlayer localPlayer = plugin.wrapPlayer(player);
            
            if (!plugin.hasPermission(player, "/regionbypass")
                    && !plugin.regionManager.getApplicableRegions(pt).canBuild(localPlayer)) {
                player.sendMessage(ChatColor.DARK_RED
                        + "You don't have permission for this area.");
                event.setCancelled(true);
                return;
            }
        }
        
        if (item != null && plugin.blacklist != null && block != null) {
            if (!plugin.blacklist.check(
                    new ItemUseBlacklistEvent(plugin.wrapPlayer(player),
                            toVector(block.getRelative(event.getBlockFace())),
                            item.getTypeId()), false, false)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Called when a player attempts to log in to the server
     *
     * @param event Relevant event details
     */
    @Override
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.enforceOneSession) {
            String name = player.getName();
            
            for (Player pl : plugin.getServer().getOnlinePlayers()) {
                if (pl.getName().equalsIgnoreCase(name)) {
                    pl.kickPlayer("Logged in from another location.");
                }
            }
        }
    }
}