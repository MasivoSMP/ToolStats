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

package lol.hyper.toolstats.tools;

import lol.hyper.hyperlib.datatypes.UUIDDataType;
import lol.hyper.toolstats.ToolStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ItemLore {

    private final ToolStats toolStats;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final Map<Component, String> plainTextCache = Collections.synchronizedMap(new WeakHashMap<>());

    public ItemLore(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    private String toPlain(Component component) {
        return plainTextCache.computeIfAbsent(component, PLAIN::serialize);
    }

    /**
     * Updates existing lore on an item.
     *
     * @param itemMeta The item's meta.
     * @param oldLine  The old line to replace.
     * @param newLine  The new line to replace oldLine.
     * @return The item's new lore.
     */
    public List<Component> updateItemLore(ItemMeta itemMeta, Component oldLine, Component newLine) {
        List<Component> itemLore;
        if (itemMeta.hasLore()) {
            itemLore = itemMeta.lore();
            String oldPlain = toPlain(oldLine);
            // keep track of line index
            // this doesn't mess the lore of existing items
            for (int x = 0; x < itemLore.size(); x++) {
                // find the old line to update, keeping index
                // this means we update this line only!
                if (oldPlain.equals(toPlain(itemLore.get(x)))) {
                    itemLore.set(x, newLine);
                    return itemLore;
                }
            }
            // if the item has lore, but we didn't find the line
            itemLore.add(newLine);
        } else {
            // if the item has no lore, create a new list and add the line
            itemLore = new ArrayList<>();
            itemLore.add(newLine);
        }
        return itemLore;
    }

    /**
     * Remove a given lore from an item.
     *
     * @param inputLore The item's lore.
     * @param toRemove  The line to remove.
     * @return The lore with the line removed.
     */
    public List<Component> removeLore(List<Component> inputLore, Component toRemove) {
        if (inputLore == null) {
            return Collections.emptyList();
        }
        List<Component> newLore = new ArrayList<>(inputLore);
        String targetPlain = toPlain(toRemove);
        newLore.removeIf(line -> targetPlain.equals(toPlain(line)));
        return newLore;
    }

    private List<Component> updateItemLoreIndexed(ItemMeta itemMeta, PersistentDataContainer container, NamespacedKey indexKey, Component oldLine, Component newLine) {
        List<Component> itemLore = itemMeta.hasLore() ? itemMeta.lore() : new ArrayList<>();
        if (itemLore == null) {
            itemLore = new ArrayList<>();
        }

        String oldPlain = toPlain(oldLine);
        Integer cachedIndex = container.get(indexKey, PersistentDataType.INTEGER);
        if (cachedIndex != null) {
            int index = cachedIndex;
            if (index >= 0 && index < itemLore.size()) {
                if (oldPlain.equals(toPlain(itemLore.get(index)))) {
                    itemLore.set(index, newLine);
                    return itemLore;
                }
            }
        }

        for (int x = 0; x < itemLore.size(); x++) {
            if (oldPlain.equals(toPlain(itemLore.get(x)))) {
                itemLore.set(x, newLine);
                container.set(indexKey, PersistentDataType.INTEGER, x);
                return itemLore;
            }
        }

        itemLore.add(newLine);
        container.set(indexKey, PersistentDataType.INTEGER, itemLore.size() - 1);
        return itemLore;
    }

    private List<Component> removeLoreIndexed(ItemMeta itemMeta, PersistentDataContainer container, NamespacedKey indexKey, Component toRemove) {
        List<Component> currentLore = itemMeta.hasLore() ? itemMeta.lore() : null;
        if (currentLore == null || currentLore.isEmpty()) {
            container.remove(indexKey);
            return Collections.emptyList();
        }

        String targetPlain = toPlain(toRemove);
        Integer cachedIndex = container.get(indexKey, PersistentDataType.INTEGER);
        if (cachedIndex != null) {
            int index = cachedIndex;
            if (index >= 0 && index < currentLore.size()) {
                if (targetPlain.equals(toPlain(currentLore.get(index)))) {
                    List<Component> newLore = new ArrayList<>(currentLore);
                    newLore.remove(index);
                    container.remove(indexKey);
                    return newLore;
                }
            }
        }

        List<Component> newLore = new ArrayList<>(currentLore);
        newLore.removeIf(line -> targetPlain.equals(toPlain(line)));
        container.remove(indexKey);
        return newLore;
    }

    /**
     * Add x to the crops mined stat.
     *
     * @param playerTool The tool to update.
     */
    public ItemMeta updateCropsMined(ItemStack playerTool, int add) {
        ItemMeta meta = playerTool.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", playerTool);
            return null;
        }
        // read the current stats from the item
        // if they don't exist, then start from 0
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.crops-harvested")) {
            if (container.has(toolStats.cropsHarvested)) {
                Integer cropsMined = container.get(toolStats.cropsHarvested, PersistentDataType.INTEGER);
                if (cropsMined == null) {
                    return null;
                }
                container.remove(toolStats.cropsHarvested);
                container.remove(toolStats.loreIndexCropsMined);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "crops-mined");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldCropsMinedFormatted = toolStats.numberFormat.formatInt(cropsMined);
                    Component lineToRemove = toolStats.configTools.formatLore("crops-harvested", "{crops}", oldCropsMinedFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexCropsMined, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "crops-mined");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.cropsHarvested) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerTool);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerTool);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Integer cropsMined = 0;
        if (container.has(toolStats.cropsHarvested, PersistentDataType.INTEGER)) {
            cropsMined = container.get(toolStats.cropsHarvested, PersistentDataType.INTEGER);
        }

        if (cropsMined == null) {
            cropsMined = 0;
            toolStats.logger.warn("{} does not have valid crops-mined set! Resting to zero. This should NEVER happen.", playerTool);
        }

        container.set(toolStats.cropsHarvested, PersistentDataType.INTEGER, cropsMined + add);
        String oldCropsMinedFormatted = toolStats.numberFormat.formatInt(cropsMined);
        String newCropsMinedFormatted = toolStats.numberFormat.formatInt(cropsMined + add);
        Component oldLine = toolStats.configTools.formatLore("crops-harvested", "{crops}", oldCropsMinedFormatted);
        Component newLine = toolStats.configTools.formatLore("crops-harvested", "{crops}", newCropsMinedFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexCropsMined, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add x to the blocks mined stat.
     *
     * @param playerTool The tool to update.
     */
    public ItemMeta updateBlocksMined(ItemStack playerTool, int add) {
        ItemMeta meta = playerTool.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", playerTool);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.configTools.checkConfig(playerTool.getType(), "blocks-mined")) {
            if (container.has(toolStats.blocksMined)) {
                Integer blocksMined = container.get(toolStats.blocksMined, PersistentDataType.INTEGER);
                if (blocksMined == null) {
                    return null;
                }
                container.remove(toolStats.blocksMined);
                container.remove(toolStats.loreIndexBlocksMined);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "blocks-mined");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldBlocksMinedFormatted = toolStats.numberFormat.formatInt(blocksMined);
                    Component lineToRemove = toolStats.configTools.formatLore("blocks-mined", "{blocks}", oldBlocksMinedFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexBlocksMined, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        boolean validToken = toolStats.itemChecker.checkTokens(container, "blocks-mined");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.blocksMined) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerTool);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerTool);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        // read the current stats from the item
        // if they don't exist, then start from 0
        Integer blocksMined = 0;
        if (container.has(toolStats.blocksMined, PersistentDataType.INTEGER)) {
            blocksMined = container.get(toolStats.blocksMined, PersistentDataType.INTEGER);
        }

        if (blocksMined == null) {
            blocksMined = 0;
            toolStats.logger.warn("{} does not have valid generic-mined set! Resting to zero. This should NEVER happen.", playerTool);
        }

        container.set(toolStats.blocksMined, PersistentDataType.INTEGER, blocksMined + add);
        String oldBlocksMinedFormatted = toolStats.numberFormat.formatInt(blocksMined);
        String newBlocksMinedFormatted = toolStats.numberFormat.formatInt(blocksMined + add);
        Component oldLine = toolStats.configTools.formatLore("blocks-mined", "{blocks}", oldBlocksMinedFormatted);
        Component newLine = toolStats.configTools.formatLore("blocks-mined", "{blocks}", newBlocksMinedFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexBlocksMined, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add +1 to the player kills stat.
     *
     * @param playerWeapon The tool to update.
     */
    public ItemMeta updatePlayerKills(ItemStack playerWeapon, int add) {
        ItemMeta meta = playerWeapon.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", playerWeapon);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.configTools.checkConfig(playerWeapon.getType(), "player-kills")) {
            if (container.has(toolStats.playerKills)) {
                Integer playerKills = container.get(toolStats.playerKills, PersistentDataType.INTEGER);
                if (playerKills == null) {
                    return null;
                }
                container.remove(toolStats.playerKills);
                container.remove(toolStats.loreIndexPlayerKills);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "player-kills");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldPlayerKillsFormatted = toolStats.numberFormat.formatInt(playerKills);
                    Component lineToRemove = toolStats.configTools.formatLore("player-kills", "{kills}", oldPlayerKillsFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexPlayerKills, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "player-kills");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.playerKills) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerWeapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerWeapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Integer playerKills = 0;
        if (container.has(toolStats.playerKills, PersistentDataType.INTEGER)) {
            playerKills = container.get(toolStats.playerKills, PersistentDataType.INTEGER);
        }

        if (playerKills == null) {
            playerKills = 0;
            toolStats.logger.warn("{} does not have valid player-kills set! Resting to zero. This should NEVER happen.", playerWeapon);
        }

        container.set(toolStats.playerKills, PersistentDataType.INTEGER, playerKills + add);
        String oldPlayerKillsFormatted = toolStats.numberFormat.formatInt(playerKills);
        String newPlayerKillsFormatted = toolStats.numberFormat.formatInt(playerKills + add);
        Component oldLine = toolStats.configTools.formatLore("kills.player", "{kills}", oldPlayerKillsFormatted);
        Component newLine = toolStats.configTools.formatLore("kills.player", "{kills}", newPlayerKillsFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexPlayerKills, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add x to the mob kills stat.
     *
     * @param playerWeapon The tool to update.
     */
    public ItemMeta updateMobKills(ItemStack playerWeapon, int add) {
        ItemMeta meta = playerWeapon.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", playerWeapon);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.configTools.checkConfig(playerWeapon.getType(), "mob-kills")) {
            if (container.has(toolStats.mobKills)) {
                Integer mobKills = container.get(toolStats.mobKills, PersistentDataType.INTEGER);
                if (mobKills == null) {
                    return null;
                }
                container.remove(toolStats.mobKills);
                container.remove(toolStats.loreIndexMobKills);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "mob-kills");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldMobKillsFormatted = toolStats.numberFormat.formatInt(mobKills);
                    Component lineToRemove = toolStats.configTools.formatLore("mob-kills", "{kills}", oldMobKillsFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexMobKills, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "mob-kills");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.mobKills) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerWeapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(playerWeapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Integer mobKills = 0;
        if (container.has(toolStats.mobKills, PersistentDataType.INTEGER)) {
            mobKills = container.get(toolStats.mobKills, PersistentDataType.INTEGER);
        }

        if (mobKills == null) {
            mobKills = 0;
            toolStats.logger.warn("{} does not have valid mob-kills set! Resting to zero. This should NEVER happen.", playerWeapon);
        }

        container.set(toolStats.mobKills, PersistentDataType.INTEGER, mobKills + add);
        String oldMobKillsFormatted = toolStats.numberFormat.formatInt(mobKills);
        String newMobKillsFormatted = toolStats.numberFormat.formatInt(mobKills + add);
        Component oldLine = toolStats.configTools.formatLore("kills.mob", "{kills}", oldMobKillsFormatted);
        Component newLine = toolStats.configTools.formatLore("kills.mob", "{kills}", newMobKillsFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexMobKills, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add damage to an armor piece.
     *
     * @param armorPiece The armor to update.
     * @param damage     The amount of damage to apply.
     * @param bypass     Bypass the negative damage check.
     */
    public ItemMeta updateArmorDamage(ItemStack armorPiece, double damage, boolean bypass) {
        // ignore if the damage is zero or negative
        if (damage < 0) {
            if (!bypass) {
                return null;
            }
        }
        ItemMeta meta = armorPiece.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", armorPiece);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.armor-damage")) {
            if (container.has(toolStats.armorDamage)) {
                Double armorDamage = container.get(toolStats.armorDamage, PersistentDataType.DOUBLE);
                if (armorDamage == null) {
                    return null;
                }
                container.remove(toolStats.armorDamage);
                container.remove(toolStats.loreIndexArmorDamage);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "damage-taken");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldDamageTakenFormatted = toolStats.numberFormat.formatDouble(armorDamage);
                    Component lineToRemove = toolStats.configTools.formatLore("damage-taken", "{damage}", oldDamageTakenFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexArmorDamage, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "damage-taken");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.armorDamage) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(armorPiece);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(armorPiece);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Double damageTaken = 0.0;
        if (container.has(toolStats.armorDamage, PersistentDataType.DOUBLE)) {
            damageTaken = container.get(toolStats.armorDamage, PersistentDataType.DOUBLE);
        }

        if (damageTaken == null) {
            damageTaken = 0.0;
            toolStats.logger.warn("{} does not have valid damage-taken set! Resting to zero. This should NEVER happen.", armorPiece);
        }

        container.set(toolStats.armorDamage, PersistentDataType.DOUBLE, damageTaken + damage);
        String oldDamageFormatted = toolStats.numberFormat.formatDouble(damageTaken);
        String newDamageFormatted = toolStats.numberFormat.formatDouble(damageTaken + damage);
        Component oldLine = toolStats.configTools.formatLore("damage-taken", "{damage}", oldDamageFormatted);
        Component newLine = toolStats.configTools.formatLore("damage-taken", "{damage}", newDamageFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexArmorDamage, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add damage to a weapon.
     *
     * @param weapon The weapon to update.
     * @param damage The amount of damage to apply.
     * @param bypass Bypass the negative damage check.
     */
    public ItemMeta updateWeaponDamage(ItemStack weapon, double damage, boolean bypass) {
        // ignore if the damage is zero or negative
        if (damage < 0) {
            if (!bypass) {
                return null;
            }
        }
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", weapon);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.configTools.checkConfig(weapon.getType(), "damage-done")) {
            if (container.has(toolStats.damageDone)) {
                Double damageDone = container.get(toolStats.damageDone, PersistentDataType.DOUBLE);
                if (damageDone == null) {
                    return null;
                }
                container.remove(toolStats.damageDone);
                container.remove(toolStats.loreIndexDamageDone);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "damage-done");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldDamageDoneFormatted = toolStats.numberFormat.formatDouble(damageDone);
                    Component lineToRemove = toolStats.configTools.formatLore("damage-done", "{damage}", oldDamageDoneFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexDamageDone, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "damage-done");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.damageDone) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(weapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(weapon);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Double damageDone = 0.0;
        if (container.has(toolStats.damageDone, PersistentDataType.DOUBLE)) {
            damageDone = container.get(toolStats.damageDone, PersistentDataType.DOUBLE);
        }

        if (damageDone == null) {
            damageDone = 0.0;
            toolStats.logger.warn("{} does not have valid damage-done set! Resting to zero. This should NEVER happen.", weapon);
        }

        container.set(toolStats.damageDone, PersistentDataType.DOUBLE, damageDone + damage);
        String oldDamageFormatted = toolStats.numberFormat.formatDouble(damageDone);
        String newDamageFormatted = toolStats.numberFormat.formatDouble(damageDone + damage);
        Component oldLine = toolStats.configTools.formatLore("damage-done", "{damage}", oldDamageFormatted);
        Component newLine = toolStats.configTools.formatLore("damage-done", "{damage}", newDamageFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexDamageDone, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add flight time to an elytra.
     *
     * @param elytra The player's elytra.
     */
    public ItemMeta updateFlightTime(ItemStack elytra, long duration) {
        ItemMeta meta = elytra.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", elytra);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.flight-time")) {
            if (container.has(toolStats.flightTime)) {
                Long flightTime = container.get(toolStats.flightTime, PersistentDataType.LONG);
                if (flightTime == null) {
                    return null;
                }
                container.remove(toolStats.flightTime);
                container.remove(toolStats.loreIndexFlightTime);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "flight-time");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    // if the old format is in the config, check to see if the old format is on the elytra
                    if (toolStats.config.getString("messages.flight-time-old") != null) {
                        String oldFormatFormatted = toolStats.numberFormat.formatDouble((double) flightTime / 1000);
                        Component oldFormat = toolStats.configTools.formatLore("flight-time-old", "{time}", oldFormatFormatted);
                        List<Component> newLore = removeLore(meta.lore(), oldFormat);
                        meta.lore(newLore);
                    }

                    Map<String, String> oldFlightTimeFormatted = toolStats.numberFormat.formatTime(flightTime);
                    Component lineToRemove = toolStats.configTools.formatLoreMultiplePlaceholders("flight-time", oldFlightTimeFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexFlightTime, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "flight-time");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.flightTime) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(elytra);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(elytra);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        // read the current stats from the item
        // if they don't exist, then start from 0
        Long flightTime = 0L;
        if (container.has(toolStats.flightTime, PersistentDataType.LONG)) {
            flightTime = container.get(toolStats.flightTime, PersistentDataType.LONG);
        }

        if (flightTime == null) {
            flightTime = 0L;
            toolStats.logger.warn("{} does not have valid flight-time set! Resting to zero. This should NEVER happen.", flightTime);
        }

        container.set(toolStats.flightTime, PersistentDataType.LONG, flightTime + duration);
        Map<String, String> oldFlightFormatted = toolStats.numberFormat.formatTime(flightTime);
        Map<String, String> newFlightFormatted = toolStats.numberFormat.formatTime(flightTime + duration);
        // if the old format is in the config, check to see if the old format is on the elytra
        if (toolStats.config.getString("messages.flight-time-old") != null) {
            if (meta.hasLore()) {
                String oldFormatFormatted = toolStats.numberFormat.formatDouble((double) flightTime / 1000);
                Component oldFormat = toolStats.configTools.formatLore("flight-time-old", "{time}", oldFormatFormatted);
                meta.lore(removeLore(meta.lore(), oldFormat));
            }
        }
        Component oldLine = toolStats.configTools.formatLoreMultiplePlaceholders("flight-time", oldFlightFormatted);
        Component newLine = toolStats.configTools.formatLoreMultiplePlaceholders("flight-time", newFlightFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexFlightTime, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add x to sheep sheared stat.
     *
     * @param shears The shears.
     */
    public ItemMeta updateSheepSheared(ItemStack shears, int add) {
        ItemMeta meta = shears.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", shears);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.sheep-sheared")) {
            if (container.has(toolStats.sheepSheared)) {
                Integer sheepSheared = container.get(toolStats.sheepSheared, PersistentDataType.INTEGER);
                if (sheepSheared == null) {
                    return null;
                }
                container.remove(toolStats.sheepSheared);
                container.remove(toolStats.loreIndexSheepSheared);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "sheep-sheared");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldSheepShearedFormatted = toolStats.numberFormat.formatDouble(sheepSheared);
                    Component lineToRemove = toolStats.configTools.formatLore("sheep-sheared", "{sheep}", oldSheepShearedFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexSheepSheared, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "sheep-sheared");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.sheepSheared) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(shears);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(shears);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Integer sheepSheared = 0;
        if (container.has(toolStats.sheepSheared, PersistentDataType.INTEGER)) {
            sheepSheared = container.get(toolStats.sheepSheared, PersistentDataType.INTEGER);
        }

        if (sheepSheared == null) {
            sheepSheared = 0;
            toolStats.logger.warn("{} does not have valid sheared set! Resting to zero. This should NEVER happen.", shears);
        }

        container.set(toolStats.sheepSheared, PersistentDataType.INTEGER, sheepSheared + add);
        String oldSheepFormatted = toolStats.numberFormat.formatInt(sheepSheared);
        String newSheepFormatted = toolStats.numberFormat.formatInt(sheepSheared + add);
        Component oldLine = toolStats.configTools.formatLore("sheep-sheared", "{sheep}", oldSheepFormatted);
        Component newLine = toolStats.configTools.formatLore("sheep-sheared", "{sheep}", newSheepFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexSheepSheared, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add x to arrows shot stat.
     *
     * @param bow The bow.
     */
    public ItemMeta updateArrowsShot(ItemStack bow, int add) {
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", bow);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.arrows-shot")) {
            if (container.has(toolStats.arrowsShot)) {
                Integer arrowsShot = container.get(toolStats.arrowsShot, PersistentDataType.INTEGER);
                if (arrowsShot == null) {
                    return null;
                }
                container.remove(toolStats.arrowsShot);
                container.remove(toolStats.loreIndexArrowsShot);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "arrows-shot");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldArrowsShotFormatted = toolStats.numberFormat.formatDouble(arrowsShot);
                    Component lineToRemove = toolStats.configTools.formatLore("arrows-shot", "{arrows}", oldArrowsShotFormatted);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexArrowsShot, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "arrows-shot");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.arrowsShot) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(bow);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(bow);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        // read the current stats from the item
        // if they don't exist, then start from 0
        Integer arrowsShot = 0;
        if (container.has(toolStats.arrowsShot, PersistentDataType.INTEGER)) {
            arrowsShot = container.get(toolStats.arrowsShot, PersistentDataType.INTEGER);
        }

        if (arrowsShot == null) {
            arrowsShot = 0;
            toolStats.logger.warn("{} does not have valid arrows-shot set! Resting to zero. This should NEVER happen.", arrowsShot);
        }

        container.set(toolStats.arrowsShot, PersistentDataType.INTEGER, arrowsShot + add);
        String oldArrowsFormatted = toolStats.numberFormat.formatInt(arrowsShot);
        String newArrowsFormatted = toolStats.numberFormat.formatInt(arrowsShot + add);
        Component oldLine = toolStats.configTools.formatLore("arrows-shot", "{arrows}", oldArrowsFormatted);
        Component newLine = toolStats.configTools.formatLore("arrows-shot", "{arrows}", newArrowsFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexArrowsShot, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Add x to fish caught stat.
     *
     * @param fishingRod The fishing rod.
     */
    public ItemMeta updateFishCaught(ItemStack fishingRod, int add) {
        ItemMeta meta = fishingRod.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", fishingRod);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // if it's disabled, don't update the stats
        // check to see if the item has the stats, remove them if it does
        if (!toolStats.config.getBoolean("enabled.fish-caught")) {
            if (container.has(toolStats.fishCaught)) {
                Integer fishCaught = container.get(toolStats.fishCaught, PersistentDataType.INTEGER);
                if (fishCaught == null) {
                    return null;
                }
                container.remove(toolStats.fishCaught);
                container.remove(toolStats.loreIndexFishCaught);
                // remove the applied token if this stat is disabled
                if (container.has(toolStats.tokenApplied)) {
                    String appliedTokens = container.get(toolStats.tokenApplied, PersistentDataType.STRING);
                    if (appliedTokens != null) {
                        // remove the token from the list
                        // if the list is empty, remove the PDC
                        // otherwise set the PDC back with the new list
                        List<String> newTokens = toolStats.itemChecker.removeToken(appliedTokens, "fish-caught");
                        if (!newTokens.isEmpty()) {
                            container.set(toolStats.tokenApplied, PersistentDataType.STRING, String.join(",", newTokens));
                        } else {
                            container.remove(toolStats.tokenApplied);
                        }
                    }
                }
                if (meta.hasLore()) {
                    String oldFishCaught = toolStats.numberFormat.formatDouble(fishCaught);
                    Component lineToRemove = toolStats.configTools.formatLore("fished.fish-caught", "{fish}", oldFishCaught);
                    List<Component> newLore = removeLoreIndexed(meta, container, toolStats.loreIndexFishCaught, lineToRemove);
                    meta.lore(newLore);
                }
                return meta;
            }
            return null;
        }

        // check for tokens
        boolean validToken = toolStats.itemChecker.checkTokens(container, "fish-caught");
        // check for tokens
        if (toolStats.config.getBoolean("tokens.enabled")) {
            // if the item has stats but no token, add the token
            if (container.has(toolStats.fishCaught) && !validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(fishingRod);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }

            // the item does not have a valid token
            if (!validToken) {
                return null;
            }
        } else {
            if (!validToken) {
                String newTokens = toolStats.itemChecker.addTokensToExisting(fishingRod);
                if (newTokens != null) {
                    container.set(toolStats.tokenApplied, PersistentDataType.STRING, newTokens);
                }
            }
        }

        Integer fishCaught = 0;
        if (container.has(toolStats.fishCaught, PersistentDataType.INTEGER)) {
            fishCaught = container.get(toolStats.fishCaught, PersistentDataType.INTEGER);
        }

        if (fishCaught == null) {
            fishCaught = 0;
            toolStats.logger.warn("{} does not have valid fish-caught set! Resting to zero. This should NEVER happen.", fishingRod);
        }

        container.set(toolStats.fishCaught, PersistentDataType.INTEGER, fishCaught + add);
        String oldFishFormatted = toolStats.numberFormat.formatInt(fishCaught);
        String newFishFormatted = toolStats.numberFormat.formatInt(fishCaught + add);
        Component oldLine = toolStats.configTools.formatLore("fished.fish-caught", "{fish}", oldFishFormatted);
        Component newLine = toolStats.configTools.formatLore("fished.fish-caught", "{fish}", newFishFormatted);
        if (oldLine == null || newLine == null) {
            return null;
        }
        List<Component> newLore = updateItemLoreIndexed(meta, container, toolStats.loreIndexFishCaught, oldLine, newLine);
        meta.lore(newLore);
        return meta;
    }

    /**
     * Format the item owner lore.
     *
     * @param playerName The player's name who owns the items.
     * @param origin     The origin type.
     * @param item       The item.
     * @return A component with the lore.
     */
    public Component formatOwner(String playerName, int origin, ItemStack item) {
        switch (origin) {
            case 0: {
                if (toolStats.configTools.checkConfig(item.getType(), "crafted-by")) {
                    return toolStats.configTools.formatLore("crafted.crafted-by", "{player}", playerName);
                }
                break;
            }
            case 2: {
                if (toolStats.configTools.checkConfig(item.getType(), "looted-by")) {
                    return toolStats.configTools.formatLore("looted.looted-by", "{player}", playerName);
                }
                break;
            }
            case 3: {
                if (toolStats.configTools.checkConfig(item.getType(), "traded-by")) {
                    return toolStats.configTools.formatLore("traded.traded-by", "{player}", playerName);
                }
                break;
            }
            case 4: {
                if (toolStats.config.getBoolean("enabled.elytra-tag")) {
                    return toolStats.configTools.formatLore("looted.found-by", "{player}", playerName);
                }
                break;
            }
            case 5: {
                if (toolStats.configTools.checkConfig(item.getType(), "fished-by")) {
                    return toolStats.configTools.formatLore("fished.caught-by", "{player}", playerName);
                }
                break;
            }
            case 6: {
                if (toolStats.configTools.checkConfig(item.getType(), "spawned-in-by")) {
                    return toolStats.configTools.formatLore("spawned-in.spawned-by", "{player}", playerName);
                }
                break;
            }
        }
        return null;
    }

    /**
     * Format the item creation time.
     *
     * @param creationDate When the item was created.
     * @param origin       The origin type.
     * @param item         The item.
     * @return A component with the lore.
     */
    public Component formatCreationTime(long creationDate, int origin, ItemStack item) {
        String date = toolStats.numberFormat.formatDate(new Date(creationDate));
        switch (origin) {
            case 0: {
                if (toolStats.configTools.checkConfig(item.getType(), "crafted-on")) {
                    return toolStats.configTools.formatLore("crafted.crafted-on", "{date}", date);
                }
                break;
            }
            case 1: {
                if (toolStats.config.getBoolean("enabled.dropped-on")) {
                    return toolStats.configTools.formatLore("dropped-on", "{date}", date);
                }
                break;
            }
            case 2: {
                if (toolStats.configTools.checkConfig(item.getType(), "looted-on")) {
                    return toolStats.configTools.formatLore("looted.looted-on", "{date}", date);
                }
                break;
            }
            case 3: {
                if (toolStats.configTools.checkConfig(item.getType(), "traded-on")) {
                    return toolStats.configTools.formatLore("traded.traded-on", "{date}", date);
                }
                break;
            }
            case 4: {
                if (toolStats.config.getBoolean("enabled.elytra-tag")) {
                    return toolStats.configTools.formatLore("looted.found-on", "{date}", date);
                }
                break;
            }
            case 5: {
                if (toolStats.configTools.checkConfig(item.getType(), "fished-on")) {
                    return toolStats.configTools.formatLore("fished.caught-on", "{date}", date);
                }
                break;
            }
            case 6: {
                if (toolStats.configTools.checkConfig(item.getType(), "spawned-in-on")) {
                    return toolStats.configTools.formatLore("spawned-in.spawned-on", "{date}", date);
                }
                break;
            }
        }
        return null;
    }

    public ItemStack addMapCreatedBy(ItemStack inputMap, Player creator) {
        if (!toolStats.config.getBoolean("enabled.map-created-by")) {
            return null;
        }
        if (inputMap.getType() != Material.FILLED_MAP) {
            return null;
        }

        ItemStack map = inputMap.clone();
        ItemMeta meta = map.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", map);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(toolStats.mapCreatedBy, new UUIDDataType())) {
            return null;
        }

        Component createdByLore = toolStats.configTools.formatLore("maps.created-by", "{player}", creator.getName());
        if (createdByLore == null) {
            return null;
        }

        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        container.set(toolStats.mapCreatedBy, new UUIDDataType(), creator.getUniqueId());
        lore.add(createdByLore);
        meta.lore(lore);
        map.setItemMeta(meta);
        return map;
    }

    public ItemStack addMapDuplicatedBy(ItemStack inputMap, Player duplicator) {
        if (!toolStats.config.getBoolean("enabled.map-duplicated-by")) {
            return null;
        }
        if (inputMap.getType() != Material.FILLED_MAP) {
            return null;
        }

        ItemStack map = inputMap.clone();
        ItemMeta meta = map.getItemMeta();
        if (meta == null) {
            toolStats.logger.warn("{} does NOT have any meta! Unable to update stats.", map);
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Component newLoreLine = toolStats.configTools.formatLore("maps.duplicated-by", "{player}", duplicator.getName());
        if (newLoreLine == null) {
            return null;
        }

        List<Component> lore;
        UUID previousDuplicator = container.get(toolStats.mapDuplicatedBy, new UUIDDataType());
        if (previousDuplicator != null) {
            String previousName = Bukkit.getOfflinePlayer(previousDuplicator).getName();
            if (previousName != null) {
                Component oldLoreLine = toolStats.configTools.formatLore("maps.duplicated-by", "{player}", previousName);
                if (oldLoreLine != null) {
                    lore = updateItemLore(meta, oldLoreLine, newLoreLine);
                } else {
                    lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    lore.add(newLoreLine);
                }
            } else {
                lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                lore.add(newLoreLine);
            }
        } else {
            lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            lore.add(newLoreLine);
        }

        container.set(toolStats.mapDuplicatedBy, new UUIDDataType(), duplicator.getUniqueId());
        meta.lore(lore);
        map.setItemMeta(meta);
        return map;
    }

    /**
     * Remove all stats, ownership, and creation time from an item.
     *
     * @param inputItem  The input item to remove stats from.
     * @param removeMeta Remove ownership and creation time?
     */
    public ItemStack removeAll(ItemStack inputItem, boolean removeMeta) {
        ItemStack finalItem = inputItem.clone();
        ItemMeta meta = finalItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // remove the applied tokens
        if (container.has(toolStats.tokenApplied)) {
            container.remove(toolStats.tokenApplied);
        }
        container.remove(toolStats.loreIndexPlayerKills);
        container.remove(toolStats.loreIndexMobKills);
        container.remove(toolStats.loreIndexBlocksMined);
        container.remove(toolStats.loreIndexCropsMined);
        container.remove(toolStats.loreIndexFishCaught);
        container.remove(toolStats.loreIndexSheepSheared);
        container.remove(toolStats.loreIndexArmorDamage);
        container.remove(toolStats.loreIndexDamageDone);
        container.remove(toolStats.loreIndexArrowsShot);
        container.remove(toolStats.loreIndexFlightTime);

        if (container.has(toolStats.playerKills)) {
            Integer playerKills = container.get(toolStats.playerKills, PersistentDataType.INTEGER);
            if (playerKills != null) {
                container.remove(toolStats.playerKills);

                String playerKillsFormatted = toolStats.numberFormat.formatInt(playerKills);
                Component lineToRemove = toolStats.configTools.formatLore("kills.player", "{kills}", playerKillsFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexPlayerKills, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.mobKills)) {
            Integer mobKills = container.get(toolStats.mobKills, PersistentDataType.INTEGER);
            if (mobKills != null) {
                container.remove(toolStats.mobKills);
                String mobKillsFormatted = toolStats.numberFormat.formatInt(mobKills);
                Component lineToRemove = toolStats.configTools.formatLore("kills.mob", "{kills}", mobKillsFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexMobKills, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.blocksMined)) {
            Integer blocksMined = container.get(toolStats.blocksMined, PersistentDataType.INTEGER);
            if (blocksMined != null) {
                container.remove(toolStats.blocksMined);
                String blocksMinedFormatted = toolStats.numberFormat.formatInt(blocksMined);
                Component lineToRemove = toolStats.configTools.formatLore("blocks-mined", "{blocks}", blocksMinedFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexBlocksMined, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.cropsHarvested)) {
            Integer cropsHarvested = container.get(toolStats.playerKills, PersistentDataType.INTEGER);
            if (cropsHarvested != null) {
                container.remove(toolStats.cropsHarvested);
                String cropsHarvestedFormatted = toolStats.numberFormat.formatInt(cropsHarvested);
                Component lineToRemove = toolStats.configTools.formatLore("crops-harvested", "{crops}", cropsHarvestedFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexCropsMined, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.fishCaught)) {
            Integer fishCaught = container.get(toolStats.fishCaught, PersistentDataType.INTEGER);
            if (fishCaught != null) {
                container.remove(toolStats.fishCaught);
                String fishCaughtFormatted = toolStats.numberFormat.formatInt(fishCaught);
                Component lineToRemove = toolStats.configTools.formatLore("fished.fish-caught", "{fish}", fishCaughtFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexFishCaught, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.sheepSheared)) {
            Integer sheepSheared = container.get(toolStats.sheepSheared, PersistentDataType.INTEGER);
            if (sheepSheared != null) {
                container.remove(toolStats.sheepSheared);
                String sheepShearedFormatted = toolStats.numberFormat.formatInt(sheepSheared);
                Component lineToRemove = toolStats.configTools.formatLore("sheep.sheared", "{sheep}", sheepShearedFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexSheepSheared, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.armorDamage)) {
            Double armorDamage = container.get(toolStats.armorDamage, PersistentDataType.DOUBLE);
            if (armorDamage != null) {
                container.remove(toolStats.armorDamage);
                String armorDamageFormatted = toolStats.numberFormat.formatDouble(armorDamage);
                Component lineToRemove = toolStats.configTools.formatLore("damage-taken", "{damage}", armorDamageFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexArmorDamage, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.damageDone)) {
            Double damageDone = container.get(toolStats.damageDone, PersistentDataType.DOUBLE);
            if (damageDone != null) {
                container.remove(toolStats.damageDone);
                String damageDoneFormatted = toolStats.numberFormat.formatDouble(damageDone);
                Component lineToRemove = toolStats.configTools.formatLore("damage-done", "{damage}", damageDoneFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexDamageDone, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.arrowsShot)) {
            Integer arrowsShot = container.get(toolStats.arrowsShot, PersistentDataType.INTEGER);
            if (arrowsShot != null) {
                container.remove(toolStats.arrowsShot);

                String arrowsShotFormatted = toolStats.numberFormat.formatInt(arrowsShot);
                Component lineToRemove = toolStats.configTools.formatLore("arrows-shot", "{arrows}", arrowsShotFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexArrowsShot, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (container.has(toolStats.flightTime)) {
            Long flightTime = container.get(toolStats.flightTime, PersistentDataType.LONG);
            if (flightTime != null) {
                container.remove(toolStats.flightTime);
                Map<String, String> flightTimeFormatted = toolStats.numberFormat.formatTime(flightTime);
                Component lineToRemove = toolStats.configTools.formatLoreMultiplePlaceholders("flight-time", flightTimeFormatted);
                meta.lore(removeLoreIndexed(meta, container, toolStats.loreIndexFlightTime, lineToRemove));
                finalItem.setItemMeta(meta);
            }
        }
        if (removeMeta) {
            Integer origin = null;
            if (container.has(toolStats.originType)) {
                origin = container.get(toolStats.originType, PersistentDataType.INTEGER);
            }

            if (container.has(toolStats.timeCreated)) {
                Long timeCreated = container.get(toolStats.timeCreated, PersistentDataType.LONG);
                if (timeCreated != null && origin != null) {
                    container.remove(toolStats.timeCreated);
                    Component timeCreatedLore = formatCreationTime(timeCreated, origin, finalItem);
                    meta.lore(removeLore(meta.lore(), timeCreatedLore));
                }
            }
            if (container.has(toolStats.itemOwner)) {
                UUID owner = container.get(toolStats.itemOwner, new UUIDDataType());
                if (owner != null && origin != null) {
                    container.remove(toolStats.itemOwner);
                    String ownerName = Bukkit.getOfflinePlayer(owner).getName();
                    if (ownerName != null) {
                        Component ownerLore = formatOwner(ownerName, origin, finalItem);
                        meta.lore(removeLore(meta.lore(), ownerLore));
                    }
                }
            }

            if (container.has(toolStats.mapCreatedBy)) {
                UUID mapCreator = container.get(toolStats.mapCreatedBy, new UUIDDataType());
                container.remove(toolStats.mapCreatedBy);
                if (mapCreator != null) {
                    String mapCreatorName = Bukkit.getOfflinePlayer(mapCreator).getName();
                    if (mapCreatorName != null) {
                        Component createdByLore = toolStats.configTools.formatLore("maps.created-by", "{player}", mapCreatorName);
                        if (createdByLore != null) {
                            meta.lore(removeLore(meta.lore(), createdByLore));
                        }
                    }
                }
            }

            if (container.has(toolStats.mapDuplicatedBy)) {
                UUID mapDuplicator = container.get(toolStats.mapDuplicatedBy, new UUIDDataType());
                container.remove(toolStats.mapDuplicatedBy);
                if (mapDuplicator != null) {
                    String mapDuplicatorName = Bukkit.getOfflinePlayer(mapDuplicator).getName();
                    if (mapDuplicatorName != null) {
                        Component duplicatedByLore = toolStats.configTools.formatLore("maps.duplicated-by", "{player}", mapDuplicatorName);
                        if (duplicatedByLore != null) {
                            meta.lore(removeLore(meta.lore(), duplicatedByLore));
                        }
                    }
                }
            }

            if (origin != null) {
                container.remove(toolStats.originType);
            }

            finalItem.setItemMeta(meta);
        }

        return finalItem;
    }
}
