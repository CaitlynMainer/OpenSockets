package me.caitlyn.opensockets;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = OpenSockets.MOD_ID, value = Side.CLIENT)
public final class OpenSocketsClient {
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(OpenSockets.SOCKET_CARD, 0,
                new ModelResourceLocation(OpenSockets.SOCKET_CARD.getRegistryName(), "inventory"));
    }

    private OpenSocketsClient() {
    }
}
