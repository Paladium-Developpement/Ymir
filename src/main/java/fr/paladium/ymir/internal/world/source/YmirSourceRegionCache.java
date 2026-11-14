package fr.paladium.ymir.internal.world.source;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirSourceRegionCache {

	private static final YmirSourceRegionCache INSTANCE = new YmirSourceRegionCache();

	private final Map<File, RandomAccessFile> files = new LinkedHashMap<>(16, 0.75F, true);

	public static YmirSourceRegionCache inst() {
		return YmirSourceRegionCache.INSTANCE;
	}

	public synchronized Optional<NBTTagCompound> read(final File directory, final int chunkX, final int chunkZ) {
		final File file = new File(new File(directory, "region"), "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
		if (!file.isFile()) {
			return Optional.empty();
		}

		try {
			final RandomAccessFile region = this.open(file);
			region.seek(4L * ((chunkX & 31) + (chunkZ & 31) * 32));
			final int location = region.readInt();
			if (location == 0) {
				return Optional.empty();
			}

			region.seek((location >> 8) * 4096L);
			final int length = region.readInt();
			final byte compression = region.readByte();
			final byte[] data = new byte[length - 1];
			region.readFully(data);
			try (final DataInputStream stream = new DataInputStream(new BufferedInputStream(this.decompress(compression, data)))) {
				return Optional.of(CompressedStreamTools.read(stream));
			}
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the source chunk " + chunkX + ", " + chunkZ + " of " + directory + ": " + e.getMessage());
			return Optional.empty();
		}
	}

	public synchronized YmirSourceRegionCache close() {
		this.files.values().forEach(this::close);
		this.files.clear();
		return this;
	}

	private RandomAccessFile open(final File file) throws IOException {
		final RandomAccessFile cached = this.files.get(file);
		if (cached != null) {
			return cached;
		}

		final RandomAccessFile region = new RandomAccessFile(file, "r");
		this.files.put(file, region);
		final Iterator<RandomAccessFile> iterator = this.files.values().iterator();
		while (this.files.size() > 64 && iterator.hasNext()) {
			this.close(iterator.next());
			iterator.remove();
		}
		return region;
	}

	private InputStream decompress(final byte compression, final byte[] data) throws IOException {
		return compression == 1 ? new GZIPInputStream(new ByteArrayInputStream(data)) : new InflaterInputStream(new ByteArrayInputStream(data));
	}

	private void close(final RandomAccessFile region) {
		try {
			region.close();
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to close a source region: " + e.getMessage());
		}
	}

}