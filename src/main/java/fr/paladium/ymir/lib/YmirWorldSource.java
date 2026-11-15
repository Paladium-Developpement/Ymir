package fr.paladium.ymir.lib;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import fr.paladium.ymir.internal.world.source.YmirSourceChunk;
import fr.paladium.ymir.internal.world.source.YmirSourceReader;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldSource {

	private final File directory;

	private int offsetX;
	private int offsetZ;
	private int minChunkX = Integer.MIN_VALUE;
	private int minChunkZ = Integer.MIN_VALUE;
	private int maxChunkX = Integer.MAX_VALUE;
	private int maxChunkZ = Integer.MAX_VALUE;
	private YmirWorldRotation rotation = YmirWorldRotation.NONE;

	public static @NonNull YmirWorldSource create(final @NonNull File directory) {
		return new YmirWorldSource(directory);
	}

	public @NonNull YmirWorldSource offset(final int chunkX, final int chunkZ) {
		this.offsetX = chunkX;
		this.offsetZ = chunkZ;
		return this;
	}

	public @NonNull YmirWorldSource rotation(final @NonNull YmirWorldRotation rotation) {
		this.rotation = rotation;
		return this;
	}

	public @NonNull YmirWorldSource bounds(final int minChunkX, final int minChunkZ, final int maxChunkX, final int maxChunkZ) {
		if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
			throw new YmirWorldException("The source bounds must go from the lowest to the highest chunk");
		}

		this.minChunkX = minChunkX;
		this.minChunkZ = minChunkZ;
		this.maxChunkX = maxChunkX;
		this.maxChunkZ = maxChunkZ;
		return this;
	}

	public boolean contains(final int chunkX, final int chunkZ) {
		return chunkX >= this.minChunkX && chunkX <= this.maxChunkX && chunkZ >= this.minChunkZ && chunkZ <= this.maxChunkZ;
	}

	public @NonNull CompletableFuture<Optional<ExtendedBlockStorage[]>> read(final int chunkX, final int chunkZ) {
		return YmirSourceReader.inst().readAsync(this, chunkX, chunkZ, true).thenApply(chunk -> chunk.map(YmirSourceChunk::getStorages));
	}

	public int getSourceChunkX(final int chunkX, final int chunkZ) {
		return this.rotation.inverse().rotateX(chunkX, chunkZ) + this.offsetX;
	}

	public int getSourceChunkZ(final int chunkX, final int chunkZ) {
		return this.rotation.inverse().rotateZ(chunkX, chunkZ) + this.offsetZ;
	}

	public double getWorldX(final double sourceX, final double sourceZ) {
		return this.rotation.rotateX(sourceX - this.offsetX * 16D, sourceZ - this.offsetZ * 16D);
	}

	public double getWorldZ(final double sourceX, final double sourceZ) {
		return this.rotation.rotateZ(sourceX - this.offsetX * 16D, sourceZ - this.offsetZ * 16D);
	}

	public int getWorldX(final int sourceX, final int sourceZ) {
		return this.rotation.rotateX(sourceX - this.offsetX * 16, sourceZ - this.offsetZ * 16);
	}

	public int getWorldZ(final int sourceX, final int sourceZ) {
		return this.rotation.rotateZ(sourceX - this.offsetX * 16, sourceZ - this.offsetZ * 16);
	}

}