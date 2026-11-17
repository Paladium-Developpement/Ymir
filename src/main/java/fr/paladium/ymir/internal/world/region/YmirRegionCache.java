package fr.paladium.ymir.internal.world.region;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.NBTTagCompound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirRegionCache {

	private static final YmirRegionCache INSTANCE = new YmirRegionCache();

	private final Map<File, Handle> handles = new LinkedHashMap<>(16, 0.75F, true);

	public static YmirRegionCache inst() {
		return YmirRegionCache.INSTANCE;
	}

	public File getFile(final File directory, final int chunkX, final int chunkZ) {
		return new File(new File(directory, "region"), "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
	}

	public boolean exists(final File file, final int chunkX, final int chunkZ, final boolean writable) throws IOException {
		final Handle handle = this.acquire(file, writable, false);
		if (handle == null) {
			return false;
		}

		try {
			return handle.getRegion().exists(chunkX, chunkZ);
		} finally {
			this.release(handle);
		}
	}

	public Optional<NBTTagCompound> read(final File file, final int chunkX, final int chunkZ, final boolean writable) throws IOException {
		final Handle handle = this.acquire(file, writable, false);
		if (handle == null) {
			return Optional.empty();
		}

		try {
			return handle.getRegion().read(chunkX, chunkZ);
		} finally {
			this.release(handle);
		}
	}

	public void write(final File file, final int chunkX, final int chunkZ, final NBTTagCompound tag) throws IOException {
		final Handle handle = this.acquire(file, true, true);
		try {
			((WritableYmirRegion) handle.getRegion()).write(chunkX, chunkZ, tag);
		} finally {
			this.release(handle);
		}
	}

	public synchronized YmirRegionCache close(final File directory) {
		final Path root = directory.toPath().toAbsolutePath().normalize();
		for (final File file : new ArrayList<>(this.handles.keySet())) {
			if (file.toPath().toAbsolutePath().normalize().startsWith(root)) {
				this.evict(file);
			}
		}
		return this;
	}

	public synchronized YmirRegionCache close() {
		new ArrayList<>(this.handles.keySet()).forEach(this::evict);
		return this;
	}

	private synchronized Handle acquire(final File file, final boolean writable, final boolean create) throws IOException {
		Handle handle = this.handles.get(file);
		while (handle != null && !this.isUsable(handle, file, writable)) {
			if (handle.getUsers() == 0) {
				this.handles.remove(file);
				this.close(handle);
			} else if (!this.await()) {
				throw new InterruptedIOException("Interrupted while waiting for the region file " + file);
			}
			handle = this.handles.get(file);
		}

		if (handle == null) {
			if (!create && !file.isFile()) {
				return null;
			}

			this.trim();
			handle = new Handle(this.open(file, writable), file.lastModified());
			this.handles.put(file, handle);
		}

		handle.users++;
		return handle;
	}

	private synchronized void release(final Handle handle) {
		if (--handle.users == 0) {
			this.notifyAll();
		}
	}

	private boolean isUsable(final Handle handle, final File file, final boolean writable) {
		if (handle.getRegion() instanceof WritableYmirRegion) {
			return true;
		}
		return !writable && handle.getModified() == file.lastModified();
	}

	private IYmirRegion open(final File file, final boolean writable) throws IOException {
		if (!writable) {
			return new ReadOnlyYmirRegion(file);
		}

		file.getParentFile().mkdirs();
		return new WritableYmirRegion(file);
	}

	private void trim() {
		final Iterator<Handle> iterator = this.handles.values().iterator();
		while (this.handles.size() >= 128 && iterator.hasNext()) {
			final Handle handle = iterator.next();
			if (handle.getUsers() == 0) {
				iterator.remove();
				this.close(handle);
			}
		}
	}

	private void evict(final File file) {
		Handle handle = this.handles.get(file);
		while (handle != null && handle.getUsers() > 0) {
			if (!this.await()) {
				return;
			}
			handle = this.handles.get(file);
		}

		if (handle != null) {
			this.handles.remove(file);
			this.close(handle);
		}
	}

	private boolean await() {
		try {
			this.wait();
			return true;
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void close(final Handle handle) {
		try {
			handle.getRegion().close();
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to close a region file: " + e.getMessage());
		}
	}

	@Getter
	@RequiredArgsConstructor
	private static final class Handle {

		private final IYmirRegion region;
		private final long        modified;

		private int users;

	}

}