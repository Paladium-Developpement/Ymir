package fr.paladium.ymir.internal.world.source;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

public class YmirSourceChunkLoader extends AnvilChunkLoader {

	private final Set<Long> regions = ConcurrentHashMap.newKeySet();

	public YmirSourceChunkLoader(final File directory) {
		super(directory);
	}

	@Override
	public boolean chunkExists(final World world, final int x, final int z) {
		return this.hasRegion(x, z) && super.chunkExists(world, x, z);
	}

	@Override
	public Object[] loadChunk__Async(final World world, final int x, final int z) throws IOException {
		return this.hasRegion(x, z) ? super.loadChunk__Async(world, x, z) : null;
	}

	@Override
	public void saveChunk(final World world, final Chunk chunk) throws MinecraftException, IOException {
		this.regions.add(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition >> 5, chunk.zPosition >> 5));
		super.saveChunk(world, chunk);
	}

	private boolean hasRegion(final int x, final int z) {
		final long key = ChunkCoordIntPair.chunkXZ2Int(x >> 5, z >> 5);
		if (this.regions.contains(key)) {
			return true;
		}

		if (!new File(new File(this.chunkSaveLocation, "region"), "r." + (x >> 5) + "." + (z >> 5) + ".mca").isFile()) {
			return false;
		}

		this.regions.add(key);
		return true;
	}

}