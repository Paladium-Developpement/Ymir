package fr.paladium.ymir.internal.server;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import fr.paladium.ymir.internal.common.CommonProxy;
import fr.paladium.ymir.internal.server.listener.YmirSpawnListener;
import fr.paladium.ymir.internal.server.task.YmirUnloadTask;
import fr.paladium.ymir.internal.world.YmirVoidWorldType;
import fr.paladium.ymir.lib.Ymir;

public class ServerProxy extends CommonProxy {

	@Override
	public void onPreInit(final FMLPreInitializationEvent event) {
		super.onPreInit(event);
		YmirVoidWorldType.register();
		super.addListener(YmirSpawnListener.class);
	}

	@Override
	public void onServerStarted(final FMLServerStartedEvent event) {
		super.onServerStarted(event);
		new YmirUnloadTask().start();
	}

	@Override
	public void onServerStopping(final FMLServerStoppingEvent event) {
		super.onServerStopping(event);
		Ymir.unloadAll();
	}

}