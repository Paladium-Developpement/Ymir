package fr.paladium.ymir.internal.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.WorldServer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldSaves {

	private static final Map<YmirWorld, Long> NEXT_SAVES = new HashMap<>();

	private static YmirWorld saving;

	public static boolean isBatched(final YmirWorld world) {
		return world.getConfig().isBatchSave() && world.getConfig().isAutoSave();
	}

	public static void tick() {
		final long now = System.currentTimeMillis();
		final List<YmirWorld> worlds = new ArrayList<>();
		for (final YmirWorld world : Ymir.getWorlds()) {
			if (YmirWorldSaves.isBatched(world) && world.isLoaded() && !YmirWorldSaves.isPendingDeletion(world)) {
				worlds.add(world);
				YmirWorldSaves.NEXT_SAVES.putIfAbsent(world, now + 30000L);
				if (YmirWorldSaves.saving == null && YmirWorldSaves.NEXT_SAVES.get(world) <= now) {
					YmirWorldSaves.saving = world;
				}
			}
		}
		YmirWorldSaves.NEXT_SAVES.keySet().retainAll(worlds);
		if (!worlds.contains(YmirWorldSaves.saving)) {
			YmirWorldSaves.saving = null;
		}

		for (final YmirWorld world : worlds) {
			final WorldServer handle = world.getHandle();
			handle.levelSaving = false;
			try {
				if (!world.getConfig().isKeepLoaded()) {
					handle.theChunkProviderServer.unloadQueuedChunks();
				}
				if (world == YmirWorldSaves.saving && handle.theChunkProviderServer.saveChunks(false, null)) {
					YmirWorldSaves.NEXT_SAVES.put(world, now + 30000L);
					YmirWorldSaves.saving = null;
				}
			} finally {
				handle.levelSaving = true;
			}
		}
	}

	private static boolean isPendingDeletion(final YmirWorld world) {
		return world.getConfig().getDeleteAfter() != null && world.getPlayers().isEmpty();
	}

}