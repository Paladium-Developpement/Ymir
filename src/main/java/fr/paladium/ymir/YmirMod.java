package fr.paladium.ymir;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import fr.paladium.ymir.internal.common.CommonProxy;

@Mod(modid = Constants.MOD_ID, version = Constants.VERSION, acceptableRemoteVersions = "*", dependencies = "required-after:palaforge-utils")
public class YmirMod {

	@Instance(Constants.MOD_ID)
	private static YmirMod instance;

	@SidedProxy(clientSide = "fr.paladium.ymir.internal.client.ClientProxy", serverSide = "fr.paladium.ymir.internal.server.ServerProxy")
	public static CommonProxy proxy;

	@EventHandler
	public void onPreInit(final FMLPreInitializationEvent event) {
		YmirMod.proxy.onPreInit(event);
	}

	@EventHandler
	public void onInit(final FMLInitializationEvent event) {
		YmirMod.proxy.onInit(event);
	}

	@EventHandler
	public void onPostInit(final FMLPostInitializationEvent event) {
		YmirMod.proxy.onPostInit(event);
	}

	@EventHandler
	public void onServerStarting(final FMLServerStartingEvent event) {
		YmirMod.proxy.onServerStarting(event);
	}

	@EventHandler
	public void onServerStarted(final FMLServerStartedEvent event) {
		YmirMod.proxy.onServerStarted(event);
	}

	@EventHandler
	public void onServerStopping(final FMLServerStoppingEvent event) {
		YmirMod.proxy.onServerStopping(event);
	}

	public static YmirMod getInstance() {
		return YmirMod.instance;
	}

}