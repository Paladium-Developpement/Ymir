package fr.paladium.ymir.internal.world;

import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.WorldServer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldUnloads {

	public static void tick() {
		for (final YmirWorld world : Ymir.getWorlds()) {
			if (!world.getConfig().isTemporary() || !world.isLoaded() || world.getPlayers().isEmpty() || !(world.getHandle().theChunkProviderServer.currentChunkLoader instanceof YmirChunkLoader)) {
				continue;
			}

			final WorldServer handle = world.getHandle();
			handle.levelSaving = false;
			try {
				handle.theChunkProviderServer.unloadQueuedChunks();
			} finally {
				handle.levelSaving = true;
			}
		}
	}

}