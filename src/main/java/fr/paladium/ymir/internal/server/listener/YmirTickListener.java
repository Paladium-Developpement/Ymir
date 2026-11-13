package fr.paladium.ymir.internal.server.listener;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import fr.paladium.ymir.internal.world.YmirWorldChunks;

public class YmirTickListener {

	@SubscribeEvent
	public void onTick(final ServerTickEvent event) {
		if (event.phase == Phase.END) {
			YmirWorldChunks.tick();
		}
	}

}