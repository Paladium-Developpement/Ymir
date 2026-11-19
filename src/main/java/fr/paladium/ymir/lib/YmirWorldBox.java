package fr.paladium.ymir.lib;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldBox {

	private final int minChunkX;
	private final int minChunkZ;
	private final int maxChunkX;
	private final int maxChunkZ;

	public static @NonNull YmirWorldBox of(final int minChunkX, final int minChunkZ, final int maxChunkX, final int maxChunkZ) {
		if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
			throw new YmirWorldException("A world box must go from the lowest to the highest chunk");
		}

		return new YmirWorldBox(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
	}

	public static @NonNull YmirWorldBox ofBlocks(final int minX, final int minZ, final int maxX, final int maxZ) {
		return YmirWorldBox.of(minX >> 4, minZ >> 4, maxX >> 4, maxZ >> 4);
	}

	public @NonNull YmirWorldBox expand(final int chunks) {
		return YmirWorldBox.of(this.minChunkX - chunks, this.minChunkZ - chunks, this.maxChunkX + chunks, this.maxChunkZ + chunks);
	}

	public boolean contains(final int chunkX, final int chunkZ) {
		return chunkX >= this.minChunkX && chunkX <= this.maxChunkX && chunkZ >= this.minChunkZ && chunkZ <= this.maxChunkZ;
	}

}