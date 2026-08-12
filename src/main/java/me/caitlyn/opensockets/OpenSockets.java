package me.caitlyn.opensockets;

import li.cil.oc.api.Driver;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Items;
import li.cil.oc.api.CreativeTab;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.concurrent.Callable;

@Mod(modid = OpenSockets.MOD_ID,
        name = OpenSockets.MOD_NAME,
        version = OpenSockets.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:opencomputers@[1.7.7,)")
@Mod.EventBusSubscriber(modid = OpenSockets.MOD_ID)
public final class OpenSockets {
    public static final String MOD_ID = "opensockets";
    public static final String MOD_NAME = "OpenSockets";
    public static final String VERSION = "0.1.0";

    public static final Item SOCKET_CARD = new Item()
            .setRegistryName(MOD_ID, "socket_card")
            .setTranslationKey(MOD_ID + ".socket_card");

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        SOCKET_CARD.setCreativeTab(CreativeTab.instance);
        event.getRegistry().register(SOCKET_CARD);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        OpenSocketsConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Driver.add(new SocketCardDriver(SOCKET_CARD));
        Items.registerFloppy("OpenSockets Examples", EnumDyeColor.CYAN,
                new Callable<li.cil.oc.api.fs.FileSystem>() {
                    @Override
                    public li.cil.oc.api.fs.FileSystem call() {
                        return FileSystem.fromClass(OpenSockets.class, MOD_ID, "socket_examples");
                    }
                }, true);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SocketManager.shutdown();
    }
}
