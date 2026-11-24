package fr.paladium.ymir.internal.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import fr.paladium.ymir.internal.world.region.YmirRegionCache;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.storage.ThreadedFileIOBase;

public class YmirChunkLoader extends AnvilChunkLoader {

	private static final long            MEMORY_BUDGET = 4L << 20;
	private static final ExecutorService KEEPER        = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "Ymir/Keeper");
		thread.setDaemon(true);
		return thread;
	});

	private final File    template;
	private final int[]   blocks;
	private final boolean memory;
	private final boolean discard;
	private final boolean keepEntities;

	private final Map<Long, NBTTagCompound>              fresh = new ConcurrentHashMap<>();
	private final Map<Long, byte[]>                      kept = new LinkedHashMap<>(16, 0.75F, true);
	private final Map<Long, long[]>                      spilled = new ConcurrentHashMap<>();
	private final Map<Long, Optional<File>>              regions = new ConcurrentHashMap<>();
	private final Map<ChunkCoordIntPair, NBTTagCompound> pending = new LinkedHashMap<>();

	private int  failures;
	private long keptBytes;
	private long spillLength;

	public YmirChunkLoader(final File directory, final File template, final int[] blocks, final boolean memory, final boolean discard, final boolean keepEntities) {
		super(directory);
		this.template = template;
		this.blocks = blocks;
		this.memory = memory;
		this.discard = discard;
		this.keepEntities = keepEntities;
		if (memory) {
			this.getSpillFile().delete();
		}
	}

	@Override
	public boolean chunkExists(final World world, final int x, final int z) {
		if (this.isKept(ChunkCoordIntPair.chunkXZ2Int(x, z)) || this.getPending(x, z) != null) {
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
		if (this.discard) {
			return;
		}

		if (!this.memory || chunk.isModified || this.keepEntities && chunk.hasEntities || this.isKept(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition))) {
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
			final long key = ChunkCoordIntPair.chunkXZ2Int(coordinates.chunkXPos, coordinates.chunkZPos);
			this.fresh.put(key, tag);
			YmirChunkLoader.KEEPER.execute(() -> this.keep(key, tag));
			return;
		}

		synchronized (this.pending) {
			this.pending.put(coordinates, tag);
		}
		ThreadedFileIOBase.threadedIOInstance.queueIO(this);
	}

	private NBTTagCompound read(final int x, final int z) throws IOException {
		final long key = ChunkCoordIntPair.chunkXZ2Int(x, z);
		final NBTTagCompound fresh = this.fresh.get(key);
		if (fresh != null) {
			return fresh;
		}

		final byte[] kept = this.getKept(key);
		if (kept != null) {
			return this.inflate(kept);
		}

		final NBTTagCompound pending = this.getPending(x, z);
		if (pending != null) {
			return pending;
		}

		final Optional<File> region = this.getRegion(x, z);
		if (!region.isPresent()) {
			return null;
		}

		final NBTTagCompound tag = YmirRegionCache.inst().read(region.get(), x, z, !this.memory).orElse(null);
		if (tag != null && this.blocks != null && region.get().toPath().startsWith(this.template.toPath())) {
			final NBTTagList sections = tag.getCompoundTag("Level").getTagList("Sections", 10);
			for (int index = 0; index < sections.tagCount(); index++) {
				YmirBlockIdRegistry.inst().convert(sections.getCompoundTagAt(index), this.blocks);
			}
		}
		return tag;
	}

	private boolean isKept(final long key) {
		synchronized (this.kept) {
			return this.fresh.containsKey(key) || this.kept.containsKey(key) || this.spilled.containsKey(key);
		}
	}

	private byte[] getKept(final long key) throws IOException {
		final long[] spill;
		synchronized (this.kept) {
			final byte[] bytes = this.kept.get(key);
			if (bytes != null) {
				return bytes;
			}

			spill = this.spilled.get(key);
		}
		if (spill == null) {
			return null;
		}

		final byte[] bytes = new byte[(int) spill[1]];
		try (final RandomAccessFile file = new RandomAccessFile(this.getSpillFile(), "r")) {
			file.seek(spill[0]);
			file.readFully(bytes);
		}
		return bytes;
	}

	private void keep(final long key, final NBTTagCompound tag) {
		final byte[] bytes;
		try {
			bytes = this.deflate(tag);
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to compress a kept chunk of " + this.chunkSaveLocation + ": " + e.getMessage());
			return;
		}

		synchronized (this.kept) {
			if (this.fresh.get(key) != tag) {
				return;
			}

			final byte[] previous = this.kept.put(key, bytes);
			this.keptBytes += bytes.length - (previous == null ? 0 : previous.length);
			this.spilled.remove(key);
			this.fresh.remove(key, tag);
		}
		this.spill();
	}

	private void spill() {
		final Map<Long, byte[]> spill = new LinkedHashMap<>();
		synchronized (this.kept) {
			long excess = this.keptBytes - YmirChunkLoader.MEMORY_BUDGET;
			final Iterator<Map.Entry<Long, byte[]>> iterator = this.kept.entrySet().iterator();
			while (excess > 0 && iterator.hasNext()) {
				final Map.Entry<Long, byte[]> entry = iterator.next();
				spill.put(entry.getKey(), entry.getValue());
				excess -= entry.getValue().length;
			}
		}
		if (spill.isEmpty() || !this.chunkSaveLocation.isDirectory()) {
			return;
		}

		try (final FileOutputStream output = new FileOutputStream(this.getSpillFile(), true)) {
			for (final Map.Entry<Long, byte[]> entry : spill.entrySet()) {
				output.write(entry.getValue());
				synchronized (this.kept) {
					if (this.kept.get(entry.getKey()) == entry.getValue()) {
						this.spilled.put(entry.getKey(), new long[] {this.spillLength, entry.getValue().length});
						this.kept.remove(entry.getKey());
						this.keptBytes -= entry.getValue().length;
					}
				}
				this.spillLength += entry.getValue().length;
			}
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to write the kept chunks of " + this.chunkSaveLocation + " to the disk: " + e.getMessage());
		}
	}

	private File getSpillFile() {
		return new File(this.chunkSaveLocation, "ymir-kept.bin");
	}

	private byte[] deflate(final NBTTagCompound tag) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(16384);
		final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try (final DataOutputStream output = new DataOutputStream(new DeflaterOutputStream(bytes, deflater))) {
			CompressedStreamTools.write(tag, output);
		} finally {
			deflater.end();
		}
		return bytes.toByteArray();
	}

	private NBTTagCompound inflate(final byte[] bytes) throws IOException {
		try (final DataInputStream input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
			return CompressedStreamTools.read(input);
		}
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