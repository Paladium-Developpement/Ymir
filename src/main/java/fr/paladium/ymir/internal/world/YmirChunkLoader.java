package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

public class YmirChunkLoader extends AnvilChunkLoader {

	private final boolean memory;
	private final Set<Long> regions = ConcurrentHashMap.newKeySet();
	private final Map<Long, NBTTagCompound> chunks = new ConcurrentHashMap<>();

	public YmirChunkLoader(final File directory, final boolean memory) {
		super(directory);
		this.memory = memory;
	}

	@Override
	public boolean chunkExists(final World world, final int x, final int z) {
		return this.chunks.containsKey(ChunkCoordIntPair.chunkXZ2Int(x, z)) || this.hasRegion(x, z) && super.chunkExists(world, x, z);
	}

	@Override
	public Object[] loadChunk__Async(final World world, final int x, final int z) throws IOException {
		final NBTTagCompound tag = this.chunks.get(ChunkCoordIntPair.chunkXZ2Int(x, z));
		if (tag != null) {
			return this.checkedReadChunkFromNBT__Async(world, x, z, tag);
		}
		return this.hasRegion(x, z) ? super.loadChunk__Async(world, x, z) : null;
	}

	@Override
	public void saveChunk(final World world, final Chunk chunk) throws MinecraftException, IOException {
		if (!this.memory) {
			this.regions.add(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition >> 5, chunk.zPosition >> 5));
			super.saveChunk(world, chunk);
			return;
		}

		if (chunk.isModified || chunk.hasEntities || this.chunks.containsKey(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition))) {
			super.saveChunk(world, chunk);
		}
	}

	@Override
	protected void addChunkToPending(final ChunkCoordIntPair coordinates, final NBTTagCompound tag) {
		if (this.memory) {
			this.chunks.put(ChunkCoordIntPair.chunkXZ2Int(coordinates.chunkXPos, coordinates.chunkZPos), tag);
			return;
		}
		super.addChunkToPending(coordinates, tag);
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