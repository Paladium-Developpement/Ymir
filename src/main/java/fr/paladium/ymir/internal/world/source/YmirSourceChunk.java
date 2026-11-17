package fr.paladium.ymir.internal.world.source;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirSourceChunk {

	private final byte[] biomes;
	private final NBTTagList entities;
	private final NBTTagList tileEntities;
	private final ExtendedBlockStorage[] storages;

	public static YmirSourceChunk of(final ExtendedBlockStorage[] storages, final byte[] biomes, final NBTTagList entities, final NBTTagList tileEntities) {
		return new YmirSourceChunk(biomes, entities, tileEntities, storages);
	}

}