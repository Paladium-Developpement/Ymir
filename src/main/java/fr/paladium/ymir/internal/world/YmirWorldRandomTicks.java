package fr.paladium.ymir.internal.world;

import java.lang.reflect.Field;

import cpw.mods.fml.relauncher.ReflectionHelper;
import fr.paladium.ymir.lib.YmirWorldException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldRandomTicks {

	private static final YmirWorldRandomTicks INSTANCE = new YmirWorldRandomTicks();

	private final Field tickRefCount = ReflectionHelper.findField(ExtendedBlockStorage.class, "field_76683_c", "tickRefCount");

	public static YmirWorldRandomTicks inst() {
		return YmirWorldRandomTicks.INSTANCE;
	}

	public void disable(final Chunk chunk) {
		for (final ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
			this.disable(storage);
		}
	}

	public void disable(final World world, final int x, final int y, final int z) {
		if (y >= 0 && y < 256 && world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
			this.disable(world.getChunkFromChunkCoords(x >> 4, z >> 4).getBlockStorageArray()[y >> 4]);
		}
	}

	private void disable(final ExtendedBlockStorage storage) {
		if (storage == null || !storage.getNeedsRandomTick()) {
			return;
		}

		try {
			this.tickRefCount.setInt(storage, 0);
		} catch (final IllegalAccessException e) {
			throw new YmirWorldException("Unable to disable the random ticks of a chunk", e);
		}
	}

}