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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.loot.Lootable;

import lol.hyper.toolstats.ToolStats;
import lol.hyper.toolstats.tools.BlockKey;
import lol.hyper.toolstats.tools.ExpiringValue;
import lol.hyper.toolstats.tools.PlayerRef;

public class PlayerInteract implements Listener {

    private final ToolStats toolStats;

    private static final long RECENT_OPEN_TTL_NANOS = TimeUnit.SECONDS.toNanos(1);

    public final Map<BlockKey, ExpiringValue<PlayerRef>> openedChests = new ConcurrentHashMap<>();
    public final Map<UUID, ExpiringValue<PlayerRef>> openedMineCarts = new ConcurrentHashMap<>();

    public PlayerInteract(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    public void trackLootableOpen(Block block, Player player) {
        BlockKey key = BlockKey.of(block);
        PlayerRef playerRef = new PlayerRef(player.getUniqueId(), player.getName());
        ExpiringValue<PlayerRef> entry = new ExpiringValue<>(playerRef, System.nanoTime() + RECENT_OPEN_TTL_NANOS);
        openedChests.put(key, entry);

        Bukkit.getAsyncScheduler().runDelayed(toolStats, scheduledTask -> openedChests.remove(key, entry), 1, TimeUnit.SECONDS);
    }

    public void trackMinecartOpen(StorageMinecart minecart, Player player) {
        UUID key = minecart.getUniqueId();
        PlayerRef playerRef = new PlayerRef(player.getUniqueId(), player.getName());
        ExpiringValue<PlayerRef> entry = new ExpiringValue<>(playerRef, System.nanoTime() + RECENT_OPEN_TTL_NANOS);
        openedMineCarts.put(key, entry);

        Bukkit.getAsyncScheduler().runDelayed(toolStats, scheduledTask -> openedMineCarts.remove(key, entry), 1, TimeUnit.SECONDS);
    }

    public PlayerRef getRecentLootableOpener(BlockKey key) {
        ExpiringValue<PlayerRef> entry = openedChests.get(key);
        if (entry == null) {
            return null;
        }

        long now = System.nanoTime();
        if (entry.isExpired(now)) {
            openedChests.remove(key, entry);
            return null;
        }

        return entry.value();
    }

    public PlayerRef getRecentMinecartOpener(UUID key) {
        ExpiringValue<PlayerRef> entry = openedMineCarts.get(key);
        if (entry == null) {
            return null;
        }

        long now = System.nanoTime();
        if (entry.isExpired(now)) {
            openedMineCarts.remove(key, entry);
            return null;
        }

        return entry.value();
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
        if (state instanceof Lootable) {
            trackLootableOpen(block, player);
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
            trackMinecartOpen(storageMinecart, player);
        }
    }
}
