/*
 * This file is part of ToolStats.
 *
 * ToolStats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ToolStats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ToolStats.  If not, see <https://www.gnu.org/licenses/>.
 */

package lol.hyper.toolstats.events;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import lol.hyper.toolstats.ToolStats;

public class PlayerInteract implements Listener {

    private final ToolStats toolStats;

    public final Map<Block, Player> openedChests = new HashMap<>();
    public final Map<StorageMinecart, Player> openedMineCarts = new HashMap<>();

    public PlayerInteract(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && !toolStats.config.getBoolean("allow-creative")) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack usedItem = event.getItem();
            if (usedItem != null && usedItem.getType() == Material.MAP && toolStats.config.getBoolean("enabled.map-created-by")) {
                PlayerInventory inventory = player.getInventory();
                EquipmentSlot usedHand = event.getHand();
                boolean usedMainHand = usedHand == null || usedHand == EquipmentSlot.HAND;
                ItemStack[] before = Arrays.stream(inventory.getContents())
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new);

                player.getScheduler().runDelayed(toolStats, scheduledTask -> {
                    // first we try the used hand
                    if (usedMainHand) {
                        ItemStack inMainHand = inventory.getItemInMainHand();
                        if (inMainHand.getType() == Material.FILLED_MAP) {
                            ItemStack updated = toolStats.itemLore.addMapCreatedBy(inMainHand, player);
                            if (updated != null) {
                                inventory.setItemInMainHand(updated);
                                return;
                            }
                        }
                    } else {
                        ItemStack inOffHand = inventory.getItemInOffHand();
                        if (inOffHand.getType() == Material.FILLED_MAP) {
                            ItemStack updated = toolStats.itemLore.addMapCreatedBy(inOffHand, player);
                            if (updated != null) {
                                inventory.setItemInOffHand(updated);
                                return;
                            }
                        }
                    }

                    // ..otherwise we scan the whole inventory for new maps
                    ItemStack[] after = inventory.getContents();
                    for (int i = 0; i < after.length; i++) {
                        ItemStack afterItem = after[i];
                        if (afterItem == null || afterItem.getType() != Material.FILLED_MAP) {
                            continue;
                        }

                        ItemStack beforeItem = before.length > i ? before[i] : null;
                        if (beforeItem != null && beforeItem.getType() == Material.FILLED_MAP) {
                            continue;
                        }

                        ItemStack updated = toolStats.itemLore.addMapCreatedBy(afterItem, player);
                        if (updated != null) {
                            inventory.setItem(i, updated);
                            return;
                        }
                    }
                }, null, 1);
            }
        }

        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // store when a player opens a chest
        BlockState state = block.getState();
        if (state instanceof InventoryHolder) {
            openedChests.put(block, player);
            Bukkit.getGlobalRegionScheduler().runDelayed(toolStats, scheduledTask -> openedChests.remove(block), 20);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && !toolStats.config.getBoolean("allow-creative")) {
            return;
        }
        // store when a player opens a minecart
        if (clicked.getType() == EntityType.CHEST_MINECART) {
            StorageMinecart storageMinecart = (StorageMinecart) clicked;
            openedMineCarts.put(storageMinecart, player);
            Bukkit.getGlobalRegionScheduler().runDelayed(toolStats, scheduledTask -> openedMineCarts.remove(storageMinecart), 20);
        }
    }
}
