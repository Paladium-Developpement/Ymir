package fr.paladium.ymir.internal.world;

import java.util.ArrayList;
import java.util.List;

import fr.paladium.ymir.lib.YmirWorldBox;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class YmirTemplateChunkProvider implements IChunkProvider {

	private final IChunkProvider        generator;
	private final YmirVoidChunkProvider empty;
	private final List<YmirWorldBox>    bounds;

	public YmirTemplateChunkProvider(final World world, final IChunkProvider generator, final List<YmirWorldBox> bounds) {
		this.generator = generator;
		this.empty = new YmirVoidChunkProvider(world);
		this.bounds = new ArrayList<>(bounds);
	}

	@Override
	public boolean canSave() {
		return this.generator.canSave();
	}

	@Override
	public String makeString() {
		return "YmirTemplate";
	}

	@Override
	public void saveExtraData() {
		this.generator.saveExtraData();
	}

	@Override
	public int getLoadedChunkCount() {
		return this.generator.getLoadedChunkCount();
	}

	@Override
	public boolean unloadQueuedChunks() {
		return this.generator.unloadQueuedChunks();
	}

	@Override
	public Chunk loadChunk(final int x, final int z) {
		return this.provideChunk(x, z);
	}

	@Override
	public Chunk provideChunk(final int x, final int z) {
		return this.isTemplate(x, z) ? this.empty.provideChunk(x, z) : this.generator.provideChunk(x, z);
	}

	@Override
	public boolean chunkExists(final int x, final int z) {
		return this.generator.chunkExists(x, z);
	}

	@Override
	public void recreateStructures(final int x, final int z) {
		if (!this.isTemplate(x, z)) {
			this.generator.recreateStructures(x, z);
		}
	}

	@Override
	public boolean saveChunks(final boolean all, final IProgressUpdate progress) {
		return this.generator.saveChunks(all, progress);
	}

	@Override
	public void populate(final IChunkProvider provider, final int x, final int z) {
		if (!this.isTemplate(x, z)) {
			this.generator.populate(provider, x, z);
		}
	}

	@Override
	public List<?> getPossibleCreatures(final EnumCreatureType type, final int x, final int y, final int z) {
		return this.generator.getPossibleCreatures(type, x, y, z);
	}

	@Override
	public ChunkPosition func_147416_a(final World world, final String structure, final int x, final int y, final int z) {
		return this.generator.func_147416_a(world, structure, x, y, z);
	}

	private boolean isTemplate(final int x, final int z) {
		for (final YmirWorldBox box : this.bounds) {
			if (box.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

}