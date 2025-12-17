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

import lol.hyper.hyperlib.datatypes.UUIDDataType;
import lol.hyper.toolstats.ToolStats;
import lol.hyper.toolstats.tools.BlockKey;
import lol.hyper.toolstats.tools.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GenerateLoot implements Listener {

    private final ToolStats toolStats;

    public GenerateLoot(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenerateLoot(LootGenerateEvent event) {
        InventoryHolder inventoryHolder = event.getInventoryHolder();
        if (inventoryHolder == null) {
            return;
        }
        Location lootLocation = event.getLootContext().getLocation();

        if (inventoryHolder instanceof StorageMinecart mineCart) {
            PlayerRef opener = toolStats.playerInteract.getRecentMinecartOpener(mineCart.getUniqueId());
            if (opener == null) {
                return;
            }
            setLoot(event.getLoot(), opener);
            return;
        }

        if (!(inventoryHolder instanceof Lootable)) {
            return;
        }

        if (lootLocation == null) {
            return;
        }

        BlockKey lootKey = BlockKey.of(lootLocation);
        PlayerRef opener = toolStats.playerInteract.getRecentLootableOpener(lootKey);
        if (opener == null) {
            opener = toolStats.playerInteract.getRecentLootableOpener(new BlockKey(lootKey.worldId(), lootKey.x() + 1, lootKey.y(), lootKey.z()));
        }
        if (opener == null) {
            opener = toolStats.playerInteract.getRecentLootableOpener(new BlockKey(lootKey.worldId(), lootKey.x() - 1, lootKey.y(), lootKey.z()));
        }
        if (opener == null) {
            opener = toolStats.playerInteract.getRecentLootableOpener(new BlockKey(lootKey.worldId(), lootKey.x(), lootKey.y(), lootKey.z() + 1));
        }
        if (opener == null) {
            opener = toolStats.playerInteract.getRecentLootableOpener(new BlockKey(lootKey.worldId(), lootKey.x(), lootKey.y(), lootKey.z() - 1));
        }
        if (opener == null) {
            return;
        }

        setLoot(event.getLoot(), opener);
    }

    /**
     * Adds lore to newly generated items.
     *
     * @param itemStack The item to add lore to.
     * @param owner     The player that found the item.
     * @return The item with the lore.
     */
    private ItemStack addLootedOrigin(ItemStack itemStack, PlayerRef owner) {
        ItemStack newItem = itemStack.clone();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }
        long timeCreated = System.currentTimeMillis();
        Date finalDate;
        if (toolStats.config.getBoolean("normalize-time-creation")) {
            finalDate = toolStats.numberFormat.normalizeTime(timeCreated);
            timeCreated = finalDate.getTime();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (container.has(toolStats.timeCreated, PersistentDataType.LONG) || container.has(toolStats.itemOwner, PersistentDataType.LONG)) {
            return null;
        }

        // get the current lore the item
        List<Component> lore;
        if (meta.hasLore()) {
            lore = meta.lore();
        } else {
            lore = new ArrayList<>();
        }

        // if creation date is enabled, add it
        Component creationDate = toolStats.itemLore.formatCreationTime(timeCreated, 2, newItem);
        if (creationDate != null) {
            container.set(toolStats.timeCreated, PersistentDataType.LONG, timeCreated);
            container.set(toolStats.originType, PersistentDataType.INTEGER, 2);
            lore.add(creationDate);
            meta.lore(lore);
        }

        // if ownership is enabled, add it
        Component itemOwner = toolStats.itemLore.formatOwner(owner.name(), 2, newItem);
        if (itemOwner != null) {
            container.set(toolStats.itemOwner, new UUIDDataType(), owner.uuid());
            container.set(toolStats.originType, PersistentDataType.INTEGER, 2);
            lore.add(itemOwner);
            meta.lore(lore);
        }

        // if hash is enabled, add it
        if (toolStats.config.getBoolean("generate-hash-for-items")) {
            String hash = toolStats.hashMaker.makeHash(newItem.getType(), owner.uuid(), timeCreated);
            container.set(toolStats.hash, PersistentDataType.STRING, hash);
        }

        newItem.setItemMeta(meta);
        return newItem;
    }

    /**
     * Add tags to the generated loot.
     *
     * @param loot   The loot from the event.
     * @param player The player triggering the event.
     */
    private void setLoot(List<ItemStack> loot, PlayerRef player) {
        for (int i = 0; i < loot.size(); i++) {
            ItemStack itemStack = loot.get(i);
            // ignore air
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }
            if (toolStats.itemChecker.isValidItem(itemStack.getType())) {
                ItemStack newItem = addLootedOrigin(itemStack, player);
                if (newItem != null) {
                    loot.set(i, newItem);
                }
            }
        }
    }
}
