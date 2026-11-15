package fr.paladium.ymir.internal.world.source;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.NBTTagCompound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirSourceRegionCache {

	private static final YmirSourceRegionCache INSTANCE = new YmirSourceRegionCache();

	private final Map<File, YmirSourceRegion> regions = new LinkedHashMap<>(16, 0.75F, true);

	public static YmirSourceRegionCache inst() {
		return YmirSourceRegionCache.INSTANCE;
	}

	public Optional<NBTTagCompound> read(final File directory, final int chunkX, final int chunkZ) {
		final File file = new File(new File(directory, "region"), "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
		try {
			return this.read(file, chunkX, chunkZ, true);
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the source chunk " + chunkX + ", " + chunkZ + " of " + directory + ": " + e.getMessage());
			return Optional.empty();
		}
	}

	public synchronized YmirSourceRegionCache close() {
		this.regions.values().forEach(this::close);
		this.regions.clear();
		return this;
	}

	private Optional<NBTTagCompound> read(final File file, final int chunkX, final int chunkZ, final boolean retry) throws IOException {
		final Optional<YmirSourceRegion> region = this.open(file);
		if (!region.isPresent()) {
			return Optional.empty();
		}

		try {
			return region.get().read(chunkX, chunkZ);
		} catch (final ClosedChannelException e) {
			this.evict(file, region.get());
			if (!retry) {
				throw e;
			}
			return this.read(file, chunkX, chunkZ, false);
		}
	}

	private synchronized Optional<YmirSourceRegion> open(final File file) throws IOException {
		final YmirSourceRegion cached = this.regions.get(file);
		if (cached != null) {
			return Optional.of(cached);
		}

		if (!file.isFile()) {
			return Optional.empty();
		}

		final YmirSourceRegion region = new YmirSourceRegion(file);
		this.regions.put(file, region);
		final Iterator<YmirSourceRegion> iterator = this.regions.values().iterator();
		while (this.regions.size() > 256 && iterator.hasNext()) {
			this.close(iterator.next());
			iterator.remove();
		}
		return Optional.of(region);
	}

	private synchronized void evict(final File file, final YmirSourceRegion region) {
		this.regions.remove(file, region);
	}

	private void close(final YmirSourceRegion region) {
		try {
			region.close();
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to close a source region: " + e.getMessage());
		}
	}

}