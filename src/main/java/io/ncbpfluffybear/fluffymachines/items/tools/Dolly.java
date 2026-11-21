package io.ncbpfluffybear.fluffymachines.items.tools;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.ncbpfluffybear.fluffymachines.utils.Utils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Dolly extends SimpleSlimefunItem<ItemUseHandler> {

    private static final ItemStack LOCK_ITEM = Utils.buildNonInteractable(
            Material.DIRT, "&4&lDolly empty", "&cHow did you get in here?"
    );

    private static final int DELAY = 500; // 500ms
    private final Map<UUID, Long> timeouts;

    public Dolly(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);
        this.timeouts = new HashMap<>();
    }

    @Nonnull
    @Override
    public ItemUseHandler getItemHandler() {
        return e -> {
            e.cancel();

            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();

            if (timeouts.containsKey(uuid) && timeouts.get(uuid) + DELAY > System.currentTimeMillis()) {
                Utils.send(p, "&cPlease wait before using the dolly again!");
                return;
            }

            ItemStack dolly = e.getItem();

            if (isStackedDolly(dolly)) {
                Utils.send(p, "&cYou cannot use stacked dollies!");
                return;
            }

            timeouts.put(uuid, System.currentTimeMillis());

            if (!e.getClickedBlock().isPresent()) {
                return;
            }

            Block b = e.getClickedBlock().get();

            // Block usage on Slimefun Blocks
            if (BlockStorage.hasBlockInfo(b)) {
                return;
            }

            if (b.getType() == Material.CHEST && Slimefun.getProtectionManager().hasPermission(
                    e.getPlayer(), b.getLocation(), Interaction.BREAK_BLOCK)
            ) {

                // Create dolly if not already one
                buildDolly(dolly, p);

                // Pick up the chest
                pickupChest(dolly, b, p);


            } else if (Slimefun.getProtectionManager().hasPermission(
                    e.getPlayer(), b.getLocation(), Interaction.PLACE_BLOCK)
            ) {

                // Place new chest
                placeChest(dolly, b.getRelative(e.getClickedFace()), p);
            }

        };
    }

    private void buildDolly(ItemStack dolly, Player p) {
        // Build backpack if new
        ItemMeta dollyMeta = dolly.getItemMeta();
        for (String line : dollyMeta.getLore()) {
            if (line.contains("ID: <ID>")) {
                PlayerProfile.get(p, profile -> {
                    int backpackId = profile.createBackpack(54).getId();
                    Slimefun.getBackpackListener().setBackpackId(p, dolly, 3, backpackId);
                    PlayerProfile.getBackpack(dolly, backpack -> backpack.getInventory().setItem(0, LOCK_ITEM));
                });
            }
        }
    }

    private void pickupChest(ItemStack dolly, Block chest, Player p) {
        PlayerProfile.getBackpack(dolly, backpack -> {
            if (backpack == null) {
                return;
            }

            Utils.runSync(new BukkitRunnable() {
                @Override
                public void run() {
                    if (isStackedDolly(dolly)
                            || chest.getType() != Material.CHEST
                            || BlockStorage.hasBlockInfo(chest)
                            || !(chest.getState() instanceof InventoryHolder holder)) {
                        return;
                    }

                    // Dolly full/empty status determined by lock item in first slot
                    // Make sure the dolly is empty
                    if (!isLockItem(backpack.getInventory().getItem(0))) {
                        Utils.send(p, "&cThis dolly is already carrying a chest!");
                        return;
                    }

                    Inventory chestInventory = holder.getInventory();
                    closeViewers(chestInventory);

                    // Update old dollies to be able to store double chests
                    if (backpack.getSize() < 54) {
                        backpack.setSize(54);
                    }

                    boolean isDoubleChest = chestInventory.getSize() == 54;
                    backpack.getInventory().setStorageContents(chestInventory.getContents());

                    // Add marker for single chests
                    if (!isDoubleChest) {
                        backpack.getInventory().setItem(27, LOCK_ITEM);
                    }

                    // Clear chest and persist backpack atomically on main thread
                    chestInventory.clear();
                    backpack.markDirty();
                    dolly.setType(Material.CHEST_MINECART);

                    if (isDoubleChest && chestInventory.getHolder() instanceof DoubleChest doubleChest) {
                        // Set other side of chest to air
                        if (((org.bukkit.block.Chest) doubleChest.getLeftSide()).getLocation().equals(chest.getLocation())) {
                            ((org.bukkit.block.Chest) doubleChest.getRightSide()).getLocation().getBlock().setType(Material.AIR);
                        } else {
                            ((org.bukkit.block.Chest) doubleChest.getLeftSide()).getLocation().getBlock().setType(Material.AIR);
                        }
                    }

                    chest.setType(Material.AIR);
                    Utils.send(p, "&aYou have picked up this chest");
                }
            });
        });
    }

    private void placeChest(ItemStack dolly, Block chestBlock, Player p) {
        PlayerProfile.getBackpack(dolly, backpack -> {
            if (backpack == null) {
                return;
            }

            Utils.runSync(new BukkitRunnable() {
                @Override
                public void run() {
                    if (isStackedDolly(dolly)) {
                        return;
                    }

                    // Update backpack size to fit doublechests
                    if (backpack.getSize() == 27) {
                        backpack.setSize(54);
                        backpack.getInventory().setItem(27, LOCK_ITEM); // Mark as single chest
                    }

                    ItemStack[] bpContents = backpack.getInventory().getContents();

                    if (isLockItem(bpContents[0])) {
                        Utils.send(p, "&cYou must pick up a chest first!");
                        return;
                    }

                    boolean singleChest = isLockItem(bpContents[27]);
                    if (!canChestFit(chestBlock, p, singleChest)) {
                        Utils.send(p, "&cYou can't fit your chest there!");
                        return;
                    }

                    createChest(chestBlock, p, singleChest);
                    backpack.getInventory().clear();
                    backpack.getInventory().setItem(0, LOCK_ITEM);
                    backpack.markDirty();

                    // Shrink contents size if single chest
                    if (singleChest) {
                        bpContents = Arrays.copyOf(bpContents, 27);
                    }

                    ((InventoryHolder) chestBlock.getState()).getInventory().setStorageContents(bpContents);
                    dolly.setType(Material.MINECART);
                    Utils.send(p, "&aChest has been placed");
                }
            });
        });
    }

    static boolean isStackedDolly(@Nullable ItemStack dolly) {
        return DollyGuard.isStackedDolly(dolly);
    }

    static void closeViewers(@Nullable Inventory inventory) {
        DollyGuard.closeViewers(inventory);
    }

    private boolean canChestFit(Block singleChestBlock, Player p, boolean singleChest) {

        boolean fits = singleChestBlock.getType() == Material.AIR;

        if (!singleChest) {
            fits = fits && getRightBlock(singleChestBlock, p.getFacing().getOppositeFace()).getType() == Material.AIR;
        }

        return fits;
    }

    private void createChest(Block firstChest, Player p, boolean singleChest) {
        BlockFace chestFace = p.getFacing().getOppositeFace();

        // Place chest and rotate
        firstChest.setType(Material.CHEST);
        Directional firstDirectional = ((Directional) firstChest.getBlockData());
        firstDirectional.setFacing(chestFace);
        firstChest.setBlockData(firstDirectional);

        if (!singleChest) {
            // Get block on right (Previous cardinal)
            Block secondChest = getRightBlock(firstChest, chestFace);

            // Place chest and rotate
            secondChest.setType(Material.CHEST);
            Directional secondDirectional = ((Directional) secondChest.getBlockData());
            secondDirectional.setFacing(chestFace);
            secondChest.setBlockData(secondDirectional);

            // Connect chests
            Chest firstChestType = ((Chest) firstChest.getBlockData());
            Chest secondChestType = ((Chest) secondChest.getBlockData());

            firstChestType.setType(Chest.Type.RIGHT); // Don't know why these are flipped
            secondChestType.setType(Chest.Type.LEFT);

            firstChest.setBlockData(firstChestType);
            secondChest.setBlockData(secondChestType);
        }
    }

    @Nonnull
    private Block getRightBlock(Block b, BlockFace face) {

        BlockFace rightFace;

        switch (face) {
            case NORTH:
                rightFace = BlockFace.WEST;
                break;
            case EAST:
                rightFace = BlockFace.NORTH;
                break;
            case SOUTH:
                rightFace = BlockFace.EAST;
                break;
            case WEST:
                rightFace = BlockFace.SOUTH;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + face);
        }

        return b.getRelative(rightFace);

    }

    private boolean isLockItem(@Nullable ItemStack lockItem) {
        return lockItem != null && (Utils.checkNonInteractable(lockItem)
                || lockItem.getItemMeta().hasCustomModelData() // Remnants of when I didn't know what PDC was
                && lockItem.getItemMeta().getCustomModelData() == 6969); // Leave in to maintain compatibility
    }

}
