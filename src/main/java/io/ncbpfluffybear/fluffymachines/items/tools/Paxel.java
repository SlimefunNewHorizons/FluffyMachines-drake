package io.ncbpfluffybear.fluffymachines.items.tools;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import io.ncbpfluffybear.fluffymachines.FluffyMachines;
import io.ncbpfluffybear.fluffymachines.utils.FluffyItems;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class Paxel extends SlimefunItem implements Listener, NotPlaceable {

    public final Set<Material> axeBlocks = Stream.of(
            Tag.LOGS.getValues(),
            Tag.PLANKS.getValues(),
            Tag.WOODEN_STAIRS.getValues(),
            Tag.SIGNS.getValues(),
            Tag.WOODEN_FENCES.getValues(),
            Tag.FENCE_GATES.getValues(),
            Tag.WOODEN_TRAPDOORS.getValues(),
            Tag.WOODEN_PRESSURE_PLATES.getValues(),
            Tag.WOODEN_DOORS.getValues(),
            Tag.WOODEN_SLABS.getValues(),
            Tag.WOODEN_BUTTONS.getValues(),
            Tag.BANNERS.getValues(),
            Tag.LEAVES.getValues(),
            new HashSet<>(Arrays.asList(Material.CHEST, Material.TRAPPED_CHEST, Material.CRAFTING_TABLE, Material.SMITHING_TABLE,
                    Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE, Material.BARREL, Material.JUKEBOX,
                    Material.CAMPFIRE, Material.BOOKSHELF, Material.JACK_O_LANTERN, Material.CARVED_PUMPKIN,
                    Material.PUMPKIN, Material.MELON, Material.COMPOSTER, Material.BEEHIVE, Material.BEE_NEST,
                    Material.NOTE_BLOCK, Material.LADDER, Material.COCOA_BEANS, Material.DAYLIGHT_DETECTOR, Material.MUSHROOM_STEM,
                    Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK, Material.BAMBOO, Material.VINE, Material.LECTERN))
    ).flatMap(Set::stream).collect(Collectors.toSet());

    /** Cada cuantos ticks se revisa el bloque apuntado para adelantar el cambio de forma. */
    private static final long INTERVALO_AJUSTE = 4L;

    /** Alcance del rayo que busca el bloque apuntado, en bloques. */
    private static final int ALCANCE_MIRA = 6;

    /** Tras golpear una entidad el paxel conserva la forma de hacha durante esta ventana. */
    private static final long GRACIA_COMBATE_MS = 2000L;

    /** Ultimo golpe a una entidad por jugador; se purga sola en cada barrido. */
    private final Map<UUID, Long> ultimoCombate = new HashMap<>();

    public Paxel(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        Bukkit.getPluginManager().registerEvents(this, FluffyMachines.getInstance());
        Bukkit.getScheduler().runTaskTimer(FluffyMachines.getInstance(), this::ajustarFormaSegunMira,
                INTERVALO_AJUSTE, INTERVALO_AJUSTE);
    }

    /**
     * Adelanta el cambio de forma al bloque que el jugador esta apuntando.
     *
     * <p>Cambiar el material del item en {@link #onMine(BlockDamageEvent)} llega tarde: el
     * cliente ya empezo a picar y, al recibir la actualizacion del slot, reinicia el progreso
     * de rotura desde cero. Esa es la sensacion de "se atasca" del ticket 358. Ajustando la
     * forma antes del primer clic el cliente nunca ve cambiar el item mientras pica.
     */
    private void ajustarFormaSegunMira() {
        long ahora = System.currentTimeMillis();
        purgarCombate(ahora);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack mano = p.getInventory().getItemInMainHand();

            if (!PaxelForm.esFormaDePaxel(mano.getType())) {
                continue;
            }

            SlimefunItem sfItem = SlimefunItem.getByItem(mano);

            if (sfItem == null || sfItem != FluffyItems.PAXEL.getItem()) {
                continue;
            }

            Long combate = ultimoCombate.get(p.getUniqueId());

            if (combate != null && ahora - combate < GRACIA_COMBATE_MS) {
                continue;
            }

            Block objetivo = p.getTargetBlockExact(ALCANCE_MIRA);

            if (objetivo == null) {
                continue;
            }

            mano.setType(PaxelForm.formaPara(objetivo.getType(), PaxelForm.esNetherita(mano.getType()),
                    SlimefunTag.EXPLOSIVE_SHOVEL_BLOCKS::isTagged, axeBlocks));
        }
    }

    private void purgarCombate(long ahora) {
        Iterator<Map.Entry<UUID, Long>> it = ultimoCombate.entrySet().iterator();

        while (it.hasNext()) {
            if (ahora - it.next().getValue() >= GRACIA_COMBATE_MS) {
                it.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onMine(BlockDamageEvent e) {
        Player p = e.getPlayer();
        SlimefunItem sfItem = SlimefunItem.getByItem(p.getInventory().getItemInMainHand());

        if (sfItem != null && sfItem == FluffyItems.PAXEL.getItem()) {
            Block b = e.getBlock();
            ItemStack item = p.getInventory().getItemInMainHand();

            // Red de seguridad: el barrido de ajustarFormaSegunMira ya suele haber dejado la
            // forma correcta, y setType no emite nada cuando el material no cambia.
            item.setType(PaxelForm.formaPara(b.getType(), PaxelForm.esNetherita(item.getType()),
                    SlimefunTag.EXPLOSIVE_SHOVEL_BLOCKS::isTagged, axeBlocks));
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();
        ItemStack item = p.getInventory().getItemInMainHand();
        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem instanceof Paxel) {
            ultimoCombate.put(p.getUniqueId(), System.currentTimeMillis());
            item.setType(PaxelForm.formaDeCombate(PaxelForm.esNetherita(item.getType())));
        }

    }
}
