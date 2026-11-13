package fr.paladium.ymir.internal.server;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import fr.paladium.ymir.internal.common.CommonProxy;
import fr.paladium.ymir.internal.server.listener.YmirConnectionListener;
import fr.paladium.ymir.internal.server.listener.YmirPlayerListener;
import fr.paladium.ymir.internal.server.listener.YmirSpawnListener;
import fr.paladium.ymir.internal.server.listener.YmirTickListener;
import fr.paladium.ymir.internal.server.task.YmirUnloadTask;
import fr.paladium.ymir.internal.world.YmirVoidWorldType;
import fr.paladium.ymir.internal.world.YmirWorldFiles;
import fr.paladium.ymir.lib.Ymir;

public class ServerProxy extends CommonProxy {

	@Override
	public void onPreInit(final FMLPreInitializationEvent event) {
		super.onPreInit(event);
		YmirVoidWorldType.register();
		super.addListener(YmirSpawnListener.class);
		super.addListener(YmirConnectionListener.class);
		super.addListener(YmirPlayerListener.class);
		super.addListener(YmirTickListener.class);
	}

	@Override
	public void onServerStarted(final FMLServerStartedEvent event) {
		super.onServerStarted(event);
		final int purged = YmirWorldFiles.purge();
		if (purged > 0) {
			System.out.println("[Ymir] " + purged + " temporary world(s) left from the last run deleted.");
		}
		new YmirUnloadTask().start();
	}

	@Override
	public void onServerStopping(final FMLServerStoppingEvent event) {
		super.onServerStopping(event);
		Ymir.unloadAll();
	}

}