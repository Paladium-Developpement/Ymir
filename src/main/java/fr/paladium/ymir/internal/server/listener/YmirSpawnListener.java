package fr.paladium.ymir.internal.server.listener;

import java.util.Optional;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fr.paladium.ymir.internal.world.YmirVoidWorldType;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldConfig;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.event.world.WorldEvent;

public class YmirSpawnListener {

	@SubscribeEvent
	public void onCreateSpawn(final WorldEvent.CreateSpawnPosition event) {
		final Optional<YmirWorld> world = Ymir.get(event.world.getWorldInfo().getWorldName());
		if (!world.isPresent()) {
			return;
		}

		final ChunkCoordinates spawn = YmirSpawnListener.spawn(world.get().getConfig());
		if (spawn == null) {
			return;
		}

		event.world.getWorldInfo().setSpawnPosition(spawn.posX, spawn.posY, spawn.posZ);
		event.setCanceled(true);
	}

	private static ChunkCoordinates spawn(final YmirWorldConfig config) {
		if (config.getSpawn() != null) {
			return config.getSpawn();
		}

		if (config.getType().isVoid()) {
			return new ChunkCoordinates(0, YmirVoidWorldType.SPAWN_HEIGHT + 1, 0);
		}

		return null;
	}

}