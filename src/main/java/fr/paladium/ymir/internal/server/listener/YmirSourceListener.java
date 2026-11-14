package fr.paladium.ymir.internal.server.listener;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fr.paladium.ymir.internal.world.source.YmirSourceChunkProvider;
import fr.paladium.ymir.lib.Ymir;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

public class YmirSourceListener {

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onLoad(final WorldEvent.Load event) {
		if (!(event.world instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.world;
		Ymir.get(world.getWorldInfo().getWorldName()).map(found -> found.getConfig().getSource()).ifPresent(source -> world.theChunkProviderServer.currentChunkProvider = new YmirSourceChunkProvider(world, source, world.theChunkProviderServer.currentChunkProvider));
	}

}