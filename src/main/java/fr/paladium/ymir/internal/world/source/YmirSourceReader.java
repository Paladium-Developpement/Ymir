package fr.paladium.ymir.internal.world.source;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.paladium.ymir.lib.YmirBlockRotationRegistry;
import fr.paladium.ymir.lib.YmirWorldRotation;
import fr.paladium.ymir.lib.YmirWorldSource;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public final class YmirSourceReader {

	private static final YmirSourceReader INSTANCE = new YmirSourceReader();

	private final int[][] permutations = new int[4][4096];
	private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
		final Thread thread = new Thread(runnable, "Ymir/Source");
		thread.setDaemon(true);
		return thread;
	});

	private YmirSourceReader() {
		for (final YmirWorldRotation rotation : YmirWorldRotation.values()) {
			for (int index = 0; index < 4096; index++) {
				final int localX = index & 15;
				final int localZ = index >> 4 & 15;
				this.permutations[rotation.getQuarters()][index] = index & ~255 | (rotation.rotateZ(localX, localZ) & 15) << 4 | rotation.rotateX(localX, localZ) & 15;
			}
		}
	}

	public static YmirSourceReader inst() {
		return YmirSourceReader.INSTANCE;
	}

	public Optional<YmirSourceChunk> read(final YmirWorldSource source, final int chunkX, final int chunkZ, final boolean sky) {
		if (!source.contains(chunkX, chunkZ)) {
			return Optional.empty();
		}

		return YmirSourceRegionCache.inst().read(source.getDirectory(), source.getSourceChunkX(chunkX, chunkZ), source.getSourceChunkZ(chunkX, chunkZ)).map(root -> this.convert(root.getCompoundTag("Level"), source, sky));
	}

	public CompletableFuture<Optional<YmirSourceChunk>> readAsync(final YmirWorldSource source, final int chunkX, final int chunkZ, final boolean sky) {
		return CompletableFuture.supplyAsync(() -> this.read(source, chunkX, chunkZ, sky), this.executor);
	}

	private YmirSourceChunk convert(final NBTTagCompound level, final YmirWorldSource source, final boolean sky) {
		final YmirWorldRotation rotation = source.getRotation();
		final int[] permutation = rotation == YmirWorldRotation.NONE ? null : this.permutations[rotation.getQuarters()];
		final ExtendedBlockStorage[] storages = new ExtendedBlockStorage[16];
		final NBTTagList sections = level.getTagList("Sections", 10);
		for (int index = 0; index < sections.tagCount(); index++) {
			final NBTTagCompound section = sections.getCompoundTagAt(index);
			final int y = section.getByte("Y");
			storages[y] = this.convert(section, y, sky, rotation, permutation);
		}

		final NBTTagList entities = level.getTagList("Entities", 10);
		for (int index = 0; index < entities.tagCount(); index++) {
			this.transformEntity(entities.getCompoundTagAt(index), source);
		}

		final NBTTagList tileEntities = level.getTagList("TileEntities", 10);
		for (int index = 0; index < tileEntities.tagCount(); index++) {
			this.transformTileEntity(tileEntities.getCompoundTagAt(index), source);
		}

		final byte[] biomes = level.hasKey("Biomes", 7) ? this.permute(level.getByteArray("Biomes"), permutation) : null;
		return YmirSourceChunk.of(storages, biomes, level.getBoolean("LightPopulated"), entities, tileEntities);
	}

	private ExtendedBlockStorage convert(final NBTTagCompound section, final int y, final boolean sky, final YmirWorldRotation rotation, final int[] permutation) {
		final ExtendedBlockStorage storage = new ExtendedBlockStorage(y << 4, sky);
		final byte[] blocks = section.getByteArray("Blocks");
		final byte[] add = section.hasKey("Add", 7) ? section.getByteArray("Add") : null;
		storage.setBlockMetadataArray(this.rotate(blocks, add, section.getByteArray("Data"), rotation, permutation));
		storage.setBlocklightArray(this.permuteNibbles(section.getByteArray("BlockLight"), permutation));
		storage.setBlockLSBArray(this.permute(blocks, permutation));
		if (add != null) {
			storage.setBlockMSBArray(this.permuteNibbles(add, permutation));
		}

		if (sky && section.hasKey("SkyLight", 7)) {
			storage.setSkylightArray(this.permuteNibbles(section.getByteArray("SkyLight"), permutation));
		}

		storage.removeInvalidBlocks();
		return storage;
	}

	private NibbleArray rotate(final byte[] blocks, final byte[] add, final byte[] data, final YmirWorldRotation rotation, final int[] permutation) {
		if (permutation == null) {
			return new NibbleArray(data, 4);
		}

		final byte[] rotated = new byte[data.length];
		for (int index = 0; index < 4096; index++) {
			final int id = blocks[index] & 255 | (add == null ? 0 : this.get(add, index) << 8);
			this.set(rotated, permutation[index], YmirBlockRotationRegistry.inst().rotate(id, this.get(data, index), rotation));
		}
		return new NibbleArray(rotated, 4);
	}

	private byte[] permute(final byte[] values, final int[] permutation) {
		if (permutation == null) {
			return values;
		}

		final byte[] permuted = new byte[values.length];
		for (int index = 0; index < values.length; index++) {
			permuted[permutation[index]] = values[index];
		}
		return permuted;
	}

	private NibbleArray permuteNibbles(final byte[] values, final int[] permutation) {
		if (permutation == null) {
			return new NibbleArray(values, 4);
		}

		final byte[] permuted = new byte[values.length];
		for (int index = 0; index < 4096; index++) {
			this.set(permuted, permutation[index], this.get(values, index));
		}
		return new NibbleArray(permuted, 4);
	}

	private int get(final byte[] nibbles, final int index) {
		return nibbles[index >> 1] >> ((index & 1) << 2) & 15;
	}

	private void set(final byte[] nibbles, final int index, final int value) {
		nibbles[index >> 1] |= value << ((index & 1) << 2);
	}

	private void transformEntity(final NBTTagCompound tag, final YmirWorldSource source) {
		final YmirWorldRotation rotation = source.getRotation();
		final NBTTagList position = tag.getTagList("Pos", 6);
		if (position.tagCount() == 3) {
			final double x = position.func_150309_d(0);
			final double z = position.func_150309_d(2);
			final NBTTagList rotated = new NBTTagList();
			rotated.appendTag(new NBTTagDouble(source.getWorldX(x, z)));
			rotated.appendTag(new NBTTagDouble(position.func_150309_d(1)));
			rotated.appendTag(new NBTTagDouble(source.getWorldZ(x, z)));
			tag.setTag("Pos", rotated);
		}

		final NBTTagList look = tag.getTagList("Rotation", 5);
		if (look.tagCount() == 2) {
			final NBTTagList rotated = new NBTTagList();
			rotated.appendTag(new NBTTagFloat(rotation.rotateYaw(look.func_150308_e(0))));
			rotated.appendTag(new NBTTagFloat(look.func_150308_e(1)));
			tag.setTag("Rotation", rotated);
		}

		if (tag.hasKey("TileX", 3) && tag.hasKey("TileZ", 3)) {
			final int x = tag.getInteger("TileX");
			final int z = tag.getInteger("TileZ");
			tag.setInteger("TileX", source.getWorldX(x, z));
			tag.setInteger("TileZ", source.getWorldZ(x, z));
		}

		if (tag.hasKey("Direction", 1)) {
			tag.setByte("Direction", (byte) (tag.getByte("Direction") + rotation.getQuarters() & 3));
			tag.removeTag("Dir");
		}

		if (tag.hasKey("Riding", 10)) {
			this.transformEntity(tag.getCompoundTag("Riding"), source);
		}
	}

	private void transformTileEntity(final NBTTagCompound tag, final YmirWorldSource source) {
		final int x = tag.getInteger("x");
		final int z = tag.getInteger("z");
		tag.setInteger("x", source.getWorldX(x, z));
		tag.setInteger("z", source.getWorldZ(x, z));
		if (tag.hasKey("Rot", 1)) {
			tag.setByte("Rot", (byte) (tag.getByte("Rot") + source.getRotation().getQuarters() * 4 & 15));
		}
	}

}