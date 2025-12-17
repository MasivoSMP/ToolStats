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

import lol.hyper.toolstats.ToolStats;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CartographyDuplicate implements Listener {

    private final ToolStats toolStats;

    public CartographyDuplicate(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCartographyResult(InventoryClickEvent event) {
        if (event.isCancelled() || event.getCurrentItem() == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE && !toolStats.config.getBoolean("allow-creative")) {
            return;
        }

        Inventory inventory = event.getClickedInventory();
        if (inventory == null || inventory.getType() != InventoryType.CARTOGRAPHY) {
            return;
        }

        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result.getType() != Material.FILLED_MAP) {
            return;
        }

        ItemStack item0 = inventory.getItem(0);
        ItemStack item1 = inventory.getItem(1);
        if (!isDuplication(item0, item1)) {
            return;
        }

        ItemStack updated = toolStats.itemLore.addMapDuplicatedBy(result, player);
        if (updated == null) {
            return;
        }

        event.setCurrentItem(updated);
        inventory.setItem(event.getSlot(), updated);
    }

    private boolean isDuplication(ItemStack item0, ItemStack item1) {
        if (item0 == null || item1 == null) {
            return false;
        }

        Material type0 = item0.getType();
        Material type1 = item1.getType();
        return (type0 == Material.FILLED_MAP && type1 == Material.MAP) || (type0 == Material.MAP && type1 == Material.FILLED_MAP);
    }
}
