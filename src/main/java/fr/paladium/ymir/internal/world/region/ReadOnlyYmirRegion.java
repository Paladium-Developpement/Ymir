package fr.paladium.ymir.internal.world.region;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public final class ReadOnlyYmirRegion implements IYmirRegion {

	private final FileChannel channel;
	private final int[] locations = new int[1024];

	public ReadOnlyYmirRegion(final File file) throws IOException {
		this.channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
		try {
			this.read(ByteBuffer.allocate(4096), 0L).asIntBuffer().get(this.locations);
		} catch (final IOException e) {
			this.channel.close();
			throw e;
		}
	}

	@Override
	public boolean exists(final int chunkX, final int chunkZ) {
		return (this.locations[(chunkX & 31) + (chunkZ & 31) * 32] & 255) != 0;
	}

	@Override
	public Optional<NBTTagCompound> read(final int chunkX, final int chunkZ) throws IOException {
		final int location = this.locations[(chunkX & 31) + (chunkZ & 31) * 32];
		if ((location & 255) == 0) {
			return Optional.empty();
		}

		final ByteBuffer buffer = this.read(ByteBuffer.allocate((location & 255) * 4096), (location >>> 8) * 4096L);
		final int length = buffer.getInt();
		if (length <= 1 || length > buffer.capacity() - 4) {
			throw new IOException("Invalid chunk length " + length);
		}

		final byte compression = buffer.get();
		try (final DataInputStream stream = new DataInputStream(new BufferedInputStream(this.decompress(compression, buffer.array(), length - 1)))) {
			return Optional.of(CompressedStreamTools.read(stream));
		}
	}

	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	private ByteBuffer read(final ByteBuffer buffer, final long position) throws IOException {
		while (buffer.hasRemaining()) {
			if (this.channel.read(buffer, position + buffer.position()) < 0) {
				break;
			}
		}

		buffer.rewind();
		return buffer;
	}

	private InputStream decompress(final byte compression, final byte[] data, final int length) throws IOException {
		final ByteArrayInputStream input = new ByteArrayInputStream(data, 5, length);
		return compression == 1 ? new GZIPInputStream(input) : new InflaterInputStream(input);
	}

}