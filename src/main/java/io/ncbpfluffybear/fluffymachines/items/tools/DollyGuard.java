package io.ncbpfluffybear.fluffymachines.items.tools;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;

final class DollyGuard {

    private DollyGuard() {
    }

    static boolean isStackedDolly(@Nullable ItemStack dolly) {
        return dolly != null && isStackedAmount(dolly.getAmount());
    }

    static boolean isStackedAmount(int amount) {
        return amount > 1;
    }

    static void closeViewers(@Nullable Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }
}
