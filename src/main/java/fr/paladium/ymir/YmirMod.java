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
import fr.paladium.ymir.common.CommonProxy;

@Mod(modid = Constants.MOD_ID, version = Constants.VERSION, acceptableRemoteVersions = "*")
public class YmirMod {	

	@Instance(Constants.MOD_ID)
	private static YmirMod instance;

	@SidedProxy(clientSide = "fr.paladium.ymir.client.ClientProxy", serverSide = "fr.paladium.ymir.server.ServerProxy")
	public static CommonProxy proxy;

	@EventHandler
	public void onPreInit(FMLPreInitializationEvent event) {
		proxy.onPreInit(event);
	}

	@EventHandler
	public void onInit(FMLInitializationEvent event) {
		proxy.onInit(event);
	}

	@EventHandler
	public void onPostInit(FMLPostInitializationEvent event) {
		proxy.onPostInit(event);
	}

	@EventHandler
	public void onServerStarting(FMLServerStartingEvent event) {
		proxy.onServerStarting(event);
	}

	@EventHandler
	public void onServerStarted(FMLServerStartedEvent event) {
		proxy.onServerStarted(event);
	}

	/* Instance */
	public static YmirMod getInstance() {
		return instance;
	}

}
