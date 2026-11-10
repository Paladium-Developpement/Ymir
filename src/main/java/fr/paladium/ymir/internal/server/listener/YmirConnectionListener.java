package fr.paladium.ymir.internal.server.listener;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerConnectionFromClientEvent;
import fr.paladium.ymir.internal.world.backend.YmirWorldBackend;
import net.minecraft.network.NetHandlerPlayServer;

public class YmirConnectionListener {

	@SubscribeEvent
	public void onConnect(final ServerConnectionFromClientEvent event) {
		YmirWorldBackend.get().connect(event.manager, ((NetHandlerPlayServer) event.handler).playerEntity);
	}

}