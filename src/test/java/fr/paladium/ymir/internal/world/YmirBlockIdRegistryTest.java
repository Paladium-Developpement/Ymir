package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import fr.paladium.ymir.internal.world.region.ReadOnlyYmirRegion;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class YmirBlockIdRegistryTest {

	private final File template = new File(System.getProperty("user.home"), "git/Paladium-BaseServer/Paladium-Dungeon-Container/data/dungeon_world");

	@Test
	public void templateBlocksKeepTheirNamesWithTheCurrentIds() throws IOException {
		Assume.assumeTrue(new File(this.template, "level.dat").isFile());

		final Map<Integer, String> names = this.readNames();
		final List<Integer> free = new ArrayList<>();
		for (int id = 1; id < 4096; id++) {
			free.add(id);
		}
		Collections.shuffle(free, new Random(42L));

		final int[] ids = new int[4096];
		final Map<Integer, String> current = new HashMap<>();
		for (int id = 1; id < 4096; id++) {
			final String name = names.get(id);
			if (name == null || !name.startsWith("propsy:") && !name.startsWith("backtothefuture:") && !name.startsWith("minecraft:")) {
				continue;
			}

			ids[id] = free.get(id - 1);
			current.put(ids[id], name);
		}

		final Map<String, Integer> counts = new HashMap<>();
		final File[] regions = new File(this.template, "region").listFiles((directory, name) -> name.endsWith(".mca"));
		Assert.assertNotNull(regions);
		for (final File file : regions) {
			try (final ReadOnlyYmirRegion region = new ReadOnlyYmirRegion(file)) {
				for (int chunkX = 0; chunkX < 32; chunkX++) {
					for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
						final Optional<NBTTagCompound> chunk = region.read(chunkX, chunkZ);
						if (!chunk.isPresent()) {
							continue;
						}

						final NBTTagList sections = chunk.get().getCompoundTag("Level").getTagList("Sections", 10);
						for (int index = 0; index < sections.tagCount(); index++) {
							this.check(sections.getCompoundTagAt(index), ids, names, current, counts);
						}
					}
				}
			}
		}

		Assert.assertEquals(Integer.valueOf(9654), counts.get("propsy:tile.block_standard_talikus_mossy_jungle_rock_bricks"));
		Assert.assertEquals(Integer.valueOf(7032), counts.get("backtothefuture:tile.invisible"));
		Assert.assertTrue(counts.keySet().stream().filter(name -> name.startsWith("propsy:")).mapToInt(counts::get).sum() > 30000);
	}

	private void check(final NBTTagCompound section, final int[] ids, final Map<Integer, String> names, final Map<Integer, String> current, final Map<String, Integer> counts) {
		final byte[] blocks = section.getByteArray("Blocks").clone();
		final byte[] add = section.hasKey("Add", 7) ? section.getByteArray("Add").clone() : new byte[2048];
		YmirBlockIdRegistry.inst().convert(section, ids);

		final byte[] convertedBlocks = section.getByteArray("Blocks");
		final byte[] convertedAdd = section.hasKey("Add", 7) ? section.getByteArray("Add") : new byte[2048];
		for (int index = 0; index < 4096; index++) {
			final int shift = (index & 1) << 2;
			final int id = blocks[index] & 255 | (add[index >> 1] >> shift & 15) << 8;
			final int converted = convertedBlocks[index] & 255 | (convertedAdd[index >> 1] >> shift & 15) << 8;
			if (id == 0) {
				Assert.assertEquals(0, converted);
				continue;
			}

			final String name = names.get(id);
			if (ids[id] == 0) {
				Assert.assertEquals("Unknown block " + name + " (" + id + ") must become air", 0, converted);
				continue;
			}

			Assert.assertEquals(name, current.get(converted));
			counts.merge(name, 1, Integer::sum);
		}
	}

	private Map<Integer, String> readNames() throws IOException {
		final Map<Integer, String> names = new HashMap<>();
		try (final InputStream stream = Files.newInputStream(new File(this.template, "level.dat").toPath())) {
			final NBTTagList entries = CompressedStreamTools.readCompressed(stream).getCompoundTag("FML").getTagList("ItemData", 10);
			for (int index = 0; index < entries.tagCount(); index++) {
				final NBTTagCompound entry = entries.getCompoundTagAt(index);
				final String key = entry.getString("K");
				if (key.length() > 1 && key.charAt(0) == '\u0001') {
					names.put(entry.getInteger("V"), key.substring(1));
				}
			}
		}
		return names;
	}

}