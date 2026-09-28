package fr.paladium.ymir.internal.world;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

@RequiredArgsConstructor
public class YmirVoidChunkProvider implements IChunkProvider {

	private final World world;

	@Override
	public boolean canSave() {
		return true;
	}

	@Override
	public String makeString() {
		return "YmirVoid";
	}

	@Override
	public void saveExtraData() {}

	@Override
	public int getLoadedChunkCount() {
		return 0;
	}

	@Override
	public boolean unloadQueuedChunks() {
		return false;
	}

	@Override
	public Chunk loadChunk(final int x, final int z) {
		return this.provideChunk(x, z);
	}

	@Override
	public Chunk provideChunk(final int x, final int z) {
		final Chunk chunk = new Chunk(this.world, x, z);
		chunk.generateSkylightMap();
		chunk.isTerrainPopulated = true;
		return chunk;
	}

	@Override
	public boolean chunkExists(final int x, final int z) {
		return true;
	}

	@Override
	public void recreateStructures(final int x, final int z) {}

	@Override
	public boolean saveChunks(final boolean all, final IProgressUpdate progress) {
		return true;
	}

	@Override
	public void populate(final IChunkProvider provider, final int x, final int z) {}

	@Override
	public List<?> getPossibleCreatures(final EnumCreatureType type, final int x, final int y, final int z) {
		return this.world.getBiomeGenForCoords(x, z).getSpawnableList(type);
	}

	@Override
	public ChunkPosition func_147416_a(final World world, final String structure, final int x, final int y, final int z) {
		return null;
	}

}