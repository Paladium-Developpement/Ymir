package fr.paladium.ymir.internal.client;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import fr.paladium.ymir.internal.client.network.YmirDimensionMessageHandler;
import fr.paladium.ymir.internal.common.CommonProxy;

public class ClientProxy extends CommonProxy {

	@Override
	public void onPreInit(final FMLPreInitializationEvent event) {
		super.onPreInit(event);
		NetworkRegistry.INSTANCE.getChannel("FORGE", Side.CLIENT).pipeline().addBefore("DimensionHandler", "YmirDimensionHandler", new YmirDimensionMessageHandler());
	}

}