package fr.paladium.ymir.internal.common;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import fr.paladium.palaforgeutils.lib.proxy.AModProxy;
import fr.paladium.ymir.internal.world.provider.YmirSkylessWorldProvider;

public abstract class CommonProxy extends AModProxy {

	@Override
	public void onPreInit(final FMLPreInitializationEvent event) {
		super.onPreInit(event);
		YmirSkylessWorldProvider.register();
	}

}