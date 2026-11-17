package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import fr.paladium.ymir.internal.world.region.YmirRegionCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.storage.ThreadedFileIOBase;

public class YmirChunkLoader extends AnvilChunkLoader {

	private final File    template;
	private final boolean memory;

	private final Map<Long, NBTTagCompound>              chunks = new ConcurrentHashMap<>();
	private final Map<Long, Optional<File>>              regions = new ConcurrentHashMap<>();
	private final Map<ChunkCoordIntPair, NBTTagCompound> pending = new LinkedHashMap<>();

	private int failures;

	public YmirChunkLoader(final File directory, final File template, final boolean memory) {
		super(directory);
		this.template = template;
		this.memory = memory;
	}

	@Override
	public boolean chunkExists(final World world, final int x, final int z) {
		if (this.chunks.containsKey(ChunkCoordIntPair.chunkXZ2Int(x, z)) || this.getPending(x, z) != null) {
			return true;
		}

		final Optional<File> region = this.getRegion(x, z);
		try {
			return region.isPresent() && YmirRegionCache.inst().exists(region.get(), x, z, !this.memory);
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the region file " + region.get() + ": " + e.getMessage());
			return false;
		}
	}

	@Override
	public Object[] loadChunk__Async(final World world, final int x, final int z) throws IOException {
		final NBTTagCompound tag = this.read(x, z);
		return tag == null ? null : this.checkedReadChunkFromNBT__Async(world, x, z, tag);
	}

	@Override
	public void saveChunk(final World world, final Chunk chunk) throws MinecraftException, IOException {
		if (!this.memory || chunk.isModified || chunk.hasEntities || this.chunks.containsKey(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition))) {
			super.saveChunk(world, chunk);
		}
	}

	@Override
	public boolean writeNextIO() {
		final ChunkCoordIntPair coordinates;
		final NBTTagCompound tag;
		synchronized (this.pending) {
			if (this.pending.isEmpty()) {
				return false;
			}

			final Map.Entry<ChunkCoordIntPair, NBTTagCompound> next = this.pending.entrySet().iterator().next();
			coordinates = next.getKey();
			tag = next.getValue();
		}

		final boolean written = this.write(coordinates, tag);
		synchronized (this.pending) {
			if (written || ++this.failures >= 5) {
				if (!written) {
					System.err.println("[Ymir] Dropping the chunk " + coordinates.chunkXPos + ", " + coordinates.chunkZPos + " of " + this.chunkSaveLocation + " after 5 failed writes.");
				}

				this.failures = 0;
				this.pending.remove(coordinates, tag);
			} else if (this.pending.remove(coordinates, tag)) {
				this.pending.put(coordinates, tag);
			}
		}
		return true;
	}

	@Override
	protected void addChunkToPending(final ChunkCoordIntPair coordinates, final NBTTagCompound tag) {
		if (this.memory) {
			this.chunks.put(ChunkCoordIntPair.chunkXZ2Int(coordinates.chunkXPos, coordinates.chunkZPos), tag);
			return;
		}

		synchronized (this.pending) {
			this.pending.put(coordinates, tag);
		}
		ThreadedFileIOBase.threadedIOInstance.queueIO(this);
	}

	private NBTTagCompound read(final int x, final int z) throws IOException {
		final NBTTagCompound memory = this.chunks.get(ChunkCoordIntPair.chunkXZ2Int(x, z));
		if (memory != null) {
			return memory;
		}

		final NBTTagCompound pending = this.getPending(x, z);
		if (pending != null) {
			return pending;
		}

		final Optional<File> region = this.getRegion(x, z);
		return region.isPresent() ? YmirRegionCache.inst().read(region.get(), x, z, !this.memory).orElse(null) : null;
	}

	private boolean write(final ChunkCoordIntPair coordinates, final NBTTagCompound tag) {
		final File file = YmirRegionCache.inst().getFile(this.chunkSaveLocation, coordinates.chunkXPos, coordinates.chunkZPos);
		try {
			YmirRegionCache.inst().write(file, coordinates.chunkXPos, coordinates.chunkZPos, tag);
			this.regions.put(ChunkCoordIntPair.chunkXZ2Int(coordinates.chunkXPos >> 5, coordinates.chunkZPos >> 5), Optional.of(file));
			return true;
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to write the chunk " + coordinates.chunkXPos + ", " + coordinates.chunkZPos + " of " + this.chunkSaveLocation + ": " + e.getMessage());
			return false;
		}
	}

	private NBTTagCompound getPending(final int x, final int z) {
		if (this.memory) {
			return null;
		}

		synchronized (this.pending) {
			return this.pending.get(new ChunkCoordIntPair(x, z));
		}
	}

	private Optional<File> getRegion(final int x, final int z) {
		return this.regions.computeIfAbsent(ChunkCoordIntPair.chunkXZ2Int(x >> 5, z >> 5), key -> {
			final File file = YmirRegionCache.inst().getFile(this.chunkSaveLocation, x, z);
			if (file.isFile()) {
				return Optional.of(file);
			}

			final File template = this.template == null ? null : YmirRegionCache.inst().getFile(this.template, x, z);
			return template != null && template.isFile() ? Optional.of(template) : Optional.empty();
		});
	}

}