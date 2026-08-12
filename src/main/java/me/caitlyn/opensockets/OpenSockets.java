package me.caitlyn.opensockets;

import li.cil.oc.api.Driver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OpenSockets.MOD_ID)
public final class OpenSockets {
    public static final String MOD_ID = "opensockets";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> SOCKET_CARD = ITEMS.register("socket_card",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public OpenSockets(IEventBus modBus, ModContainer modContainer) {
        ITEMS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, OpenSocketsConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeContents);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
                SocketManager.shutdown());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Driver.add(new SocketCardDriver(SOCKET_CARD.get()));
            registerLegacyLootDisk();
        });
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("opencomputers", "main"))) {
            event.accept(SOCKET_CARD);
        }
    }

    /**
     * OC:R 1.9.4-2 predates the datapack loot-disk reload listener. Keep the
     * examples floppy usable with that runtime, while letting newer runtimes
     * consume data/opensockets/opencomputers/loot_disks/socket_examples.json.
     */
    private static void registerLegacyLootDisk() {
        try {
            Class<?> lootClass = Class.forName("li.cil.oc.common.Loot$");
            if (Arrays.stream(lootClass.getMethods()).anyMatch(method -> method.getName().equals("addReloadListener"))) {
                return;
            }

            Object loot = lootClass.getField("MODULE$").get(null);
            Class<?> fileSystemClass = Class.forName("li.cil.oc.server.fs.FileSystem$");
            Object fileSystem = fileSystemClass.getField("MODULE$").get(null);
            Method fromResource = fileSystemClass.getMethod("fromResource", ResourceLocation.class);
            ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(MOD_ID, "socket_examples");
            Callable<Object> factory = () -> fromResource.invoke(fileSystem, resource);

            Method register = lootClass.getMethod("registerLootDisk", String.class, ResourceLocation.class,
                    DyeColor.class, Callable.class, boolean.class);
            Object stack = register.invoke(loot, "OpenSockets Examples", resource, DyeColor.CYAN, factory, true);

            Object globalDisks = lootClass.getMethod("globalDisks").invoke(loot);
            Class<?> tuple = Class.forName("scala.Tuple2");
            Object entry = tuple.getConstructor(Object.class, Object.class).newInstance(stack, 0);
            globalDisks.getClass().getMethod("$plus$eq", Object.class).invoke(globalDisks, entry);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Could not register the legacy OpenSockets examples floppy", exception);
        }
    }
}
