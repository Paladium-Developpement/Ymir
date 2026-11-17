package fr.paladium.ymir.internal.server.listener;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fr.paladium.ymir.internal.world.YmirRandomTickWorldAccess;
import fr.paladium.ymir.internal.world.YmirWorldRandomTicks;
import fr.paladium.ymir.lib.Ymir;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class YmirRandomTickListener {

	@SubscribeEvent
	public void onLoad(final WorldEvent.Load event) {
		if (this.isDisabled(event.world)) {
			event.world.addWorldAccess(new YmirRandomTickWorldAccess(event.world));
		}
	}

	@SubscribeEvent
	public void onChunkLoad(final ChunkEvent.Load event) {
		if (this.isDisabled(event.world)) {
			YmirWorldRandomTicks.inst().disable(event.getChunk());
		}
	}

	private boolean isDisabled(final World world) {
		return Ymir.get(world.getWorldInfo().getWorldName()).filter(ymir -> !ymir.getConfig().isRandomTicks()).isPresent();
	}

}