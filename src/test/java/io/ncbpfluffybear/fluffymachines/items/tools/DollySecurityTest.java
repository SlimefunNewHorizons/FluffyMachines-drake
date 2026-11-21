package io.ncbpfluffybear.fluffymachines.items.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class DollySecurityTest {

    @Test
    void isStackedAmountDetectsAmountGreaterThanOne() {
        assertFalse(DollyGuard.isStackedDolly(null));
        assertFalse(DollyGuard.isStackedAmount(0));
        assertFalse(DollyGuard.isStackedAmount(1));
        assertTrue(DollyGuard.isStackedAmount(2));
        assertTrue(DollyGuard.isStackedAmount(64));
    }

    @Test
    void closeViewersClosesAllOpenInventoryViewersSafely() {
        DollyGuard.closeViewers(null);

        AtomicInteger closed = new AtomicInteger();
        HumanEntity v1 = (HumanEntity) Proxy.newProxyInstance(
                HumanEntity.class.getClassLoader(),
                new Class<?>[]{HumanEntity.class},
                (proxy, method, args) -> {
                    if ("closeInventory".equals(method.getName())) {
                        closed.incrementAndGet();
                    }
                    return null;
                }
        );
        HumanEntity v2 = (HumanEntity) Proxy.newProxyInstance(
                HumanEntity.class.getClassLoader(),
                new Class<?>[]{HumanEntity.class},
                (proxy, method, args) -> {
                    if ("closeInventory".equals(method.getName())) {
                        closed.incrementAndGet();
                    }
                    return null;
                }
        );
        Inventory inventory = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, args) -> {
                    if ("getViewers".equals(method.getName())) {
                        return List.of(v1, v2);
                    }
                    return null;
                }
        );

        DollyGuard.closeViewers(inventory);
        assertEquals(2, closed.get());
    }
}
