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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class PlayerMove implements Listener {

    private final ToolStats toolStats;
    private final Map<UUID, Long> playerStartFlight = new ConcurrentHashMap<>();

    public PlayerMove(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE && !toolStats.config.getBoolean("allow-creative")) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (event.isGliding()) {
            playerStartFlight.putIfAbsent(playerId, System.currentTimeMillis());
            return;
        }

        Long startTime = playerStartFlight.remove(playerId);
        if (startTime == null) {
            return;
        }

        long duration = System.currentTimeMillis() - startTime;
        if (duration <= 0) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] armor = inventory.getArmorContents().clone();
        for (ItemStack armorPiece : armor) {
            if (armorPiece == null) {
                continue;
            }
            if (toolStats.itemChecker.canGlide(armorPiece)) {
                ItemMeta newMeta = toolStats.itemLore.updateFlightTime(armorPiece, duration);
                if (newMeta != null) {
                    armorPiece.setItemMeta(newMeta);
                }
            }
        }
        inventory.setArmorContents(armor);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerStartFlight.remove(event.getPlayer().getUniqueId());
    }
}
