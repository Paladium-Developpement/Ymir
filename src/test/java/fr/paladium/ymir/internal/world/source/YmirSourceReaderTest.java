package fr.paladium.ymir.internal.world.source;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import fr.paladium.ymir.internal.world.region.ReadOnlyYmirRegion;
import fr.paladium.ymir.lib.YmirBlockRotationRegistry;
import fr.paladium.ymir.lib.YmirWorldRotation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class YmirSourceReaderTest {

	private final int[][] permutations = new int[4][4096];

	public YmirSourceReaderTest() {
		for (final YmirWorldRotation rotation : YmirWorldRotation.values()) {
			for (int index = 0; index < 4096; index++) {
				final int localX = index & 15;
				final int localZ = index >> 4 & 15;
				this.permutations[rotation.getQuarters()][index] = index & ~255 | (rotation.rotateZ(localX, localZ) & 15) << 4 | rotation.rotateX(localX, localZ) & 15;
			}
		}
	}

	@Test
	public void rotatedSectionsMatchThePreviousRotationOnRealChunks() throws Exception {
		final List<File> regions = this.findRegions();
		Assume.assumeFalse(regions.isEmpty());

		final Random random = new Random(42L);
		final byte[][] table = new byte[4][65536];
		for (int index = 0; index < 65536; index++) {
			table[0][index] = (byte) (index & 15);
			for (int quarters = 1; quarters < 4; quarters++) {
				table[quarters][index] = (byte) random.nextInt(16);
			}
		}
		final Field cached = YmirBlockRotationRegistry.class.getDeclaredField("table");
		cached.setAccessible(true);
		cached.set(YmirBlockRotationRegistry.inst(), table);

		final int[] flags = new int[4096];
		for (int id = 1; id < flags.length; id++) {
			flags[id] = random.nextInt(3) == 0 ? 3 : 1;
		}

		final YmirSourceReader reader = YmirSourceReader.inst();
		final Method convert = YmirSourceReader.class.getDeclaredMethod("convert", NBTTagCompound.class, int.class, boolean.class, int[].class, int[].class, YmirWorldRotation.class, int[].class);
		final Method permute = YmirSourceReader.class.getDeclaredMethod("permute", byte[].class, int[].class);
		final Field sources = YmirSourceReader.class.getDeclaredField("sources");
		convert.setAccessible(true);
		permute.setAccessible(true);
		sources.setAccessible(true);

		int sections = 0;
		for (final File file : regions) {
			try (final ReadOnlyYmirRegion region = new ReadOnlyYmirRegion(file)) {
				for (int chunkX = 0; chunkX < 32; chunkX++) {
					for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
						final Optional<NBTTagCompound> chunk = region.read(chunkX, chunkZ);
						if (!chunk.isPresent()) {
							continue;
						}

						final NBTTagCompound level = chunk.get().getCompoundTag("Level");
						final NBTTagList list = level.getTagList("Sections", 10);
						for (final YmirWorldRotation rotation : YmirWorldRotation.values()) {
							final int[] permutation = rotation == YmirWorldRotation.NONE ? null : this.permutations[rotation.getQuarters()];
							final int[] source = rotation == YmirWorldRotation.NONE ? null : ((int[][]) sources.get(reader))[rotation.getQuarters()];
							if (level.hasKey("Biomes", 7)) {
								Assert.assertArrayEquals(this.permute(level.getByteArray("Biomes"), permutation), (byte[]) permute.invoke(reader, level.getByteArray("Biomes"), source));
							}

							for (int index = 0; index < list.tagCount(); index++) {
								final NBTTagCompound section = list.getCompoundTagAt(index);
								for (final boolean sky : new boolean[] { true, false }) {
									final ExtendedBlockStorage expected = this.convert(section, section.getByte("Y"), sky, flags, rotation, permutation);
									final ExtendedBlockStorage actual = (ExtendedBlockStorage) convert.invoke(reader, section, section.getByte("Y"), sky, null, flags, rotation, source);
									this.assertSame(expected, actual, file.getName() + " " + chunkX + "," + chunkZ + " section " + section.getByte("Y") + " " + rotation + " sky " + sky);
								}
								sections++;
							}
						}
					}
				}
			}
		}
		Assert.assertTrue(sections > 0);
	}

	private void assertSame(final ExtendedBlockStorage expected, final ExtendedBlockStorage actual, final String where) throws Exception {
		Assert.assertArrayEquals(where, expected.getBlockLSBArray(), actual.getBlockLSBArray());
		Assert.assertArrayEquals(where, this.data(expected.getBlockMSBArray()), this.data(actual.getBlockMSBArray()));
		Assert.assertArrayEquals(where, this.data(expected.getMetadataArray()), this.data(actual.getMetadataArray()));
		Assert.assertArrayEquals(where, this.data(expected.getBlocklightArray()), this.data(actual.getBlocklightArray()));
		Assert.assertArrayEquals(where, this.data(expected.getSkylightArray()), this.data(actual.getSkylightArray()));
		for (final String name : new String[] { "blockRefCount", "tickRefCount" }) {
			final Field field = ExtendedBlockStorage.class.getDeclaredField(name);
			field.setAccessible(true);
			Assert.assertEquals(where + " " + name, field.getInt(expected), field.getInt(actual));
		}
	}

	private byte[] data(final NibbleArray array) {
		return array == null ? null : array.data;
	}

	private List<File> findRegions() {
		final List<File> regions = new ArrayList<>();
		final String maps = System.getenv("YMIR_SOURCE_MAPS");
		if (maps == null) {
			return regions;
		}

		for (final String path : maps.split(File.pathSeparator)) {
			final File[] files = new File(path, "region").listFiles((directory, name) -> name.endsWith(".mca"));
			if (files != null) {
				for (final File file : files) {
					regions.add(file);
				}
			}
		}
		return regions;
	}

	private ExtendedBlockStorage convert(final NBTTagCompound section, final int y, final boolean sky, final int[] flags, final YmirWorldRotation rotation, final int[] permutation) throws Exception {
		final ExtendedBlockStorage storage = new ExtendedBlockStorage(y << 4, sky);
		final byte[] blocks = section.getByteArray("Blocks");
		final byte[] add = section.hasKey("Add", 7) ? section.getByteArray("Add") : null;
		int blockRefs = 0;
		int tickRefs = 0;
		for (int index = 0; index < 4096; index++) {
			final int id = blocks[index] & 255 | (add == null ? 0 : this.get(add, index) << 8);
			blockRefs += flags[id] & 1;
			tickRefs += flags[id] >> 1;
		}

		storage.setBlockMetadataArray(this.rotate(blocks, add, section.getByteArray("Data"), rotation, permutation));
		storage.setBlocklightArray(this.permuteNibbles(section.getByteArray("BlockLight"), permutation));
		storage.setBlockLSBArray(this.permute(blocks, permutation));
		if (add != null) {
			storage.setBlockMSBArray(this.permuteNibbles(add, permutation));
		}

		if (sky && section.hasKey("SkyLight", 7)) {
			storage.setSkylightArray(this.permuteNibbles(section.getByteArray("SkyLight"), permutation));
		}

		for (final String name : new String[] { "blockRefCount", "tickRefCount" }) {
			final Field field = ExtendedBlockStorage.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setInt(storage, name.equals("blockRefCount") ? blockRefs : tickRefs);
		}
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

}