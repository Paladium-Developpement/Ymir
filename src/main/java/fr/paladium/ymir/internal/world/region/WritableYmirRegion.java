package fr.paladium.ymir.internal.world.region;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.storage.RegionFile;

public final class WritableYmirRegion implements IYmirRegion {

	private final RegionFile file;

	public WritableYmirRegion(final File file) {
		this.file = new RegionFile(file);
	}

	@Override
	public boolean exists(final int chunkX, final int chunkZ) {
		return this.file.chunkExists(chunkX & 31, chunkZ & 31);
	}

	@Override
	public Optional<NBTTagCompound> read(final int chunkX, final int chunkZ) throws IOException {
		final DataInputStream input = this.file.getChunkDataInputStream(chunkX & 31, chunkZ & 31);
		if (input == null) {
			return Optional.empty();
		}

		try (final DataInputStream stream = input) {
			return Optional.of(CompressedStreamTools.read(stream));
		}
	}

	public void write(final int chunkX, final int chunkZ, final NBTTagCompound tag) throws IOException {
		try (final DataOutputStream stream = this.file.getChunkDataOutputStream(chunkX & 31, chunkZ & 31)) {
			CompressedStreamTools.write(tag, stream);
		}
	}

	@Override
	public void close() throws IOException {
		this.file.close();
	}

}