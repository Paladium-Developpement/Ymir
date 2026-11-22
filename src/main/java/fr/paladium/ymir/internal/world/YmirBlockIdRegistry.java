package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.registry.GameData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirBlockIdRegistry {

	private static final YmirBlockIdRegistry INSTANCE = new YmirBlockIdRegistry();

	private final Map<File, Optional<int[]>> blocks = new ConcurrentHashMap<>();

	public static YmirBlockIdRegistry inst() {
		return YmirBlockIdRegistry.INSTANCE;
	}

	public Optional<int[]> getBlocks(final File directory) {
		return this.blocks.computeIfAbsent(directory, this::load);
	}

	public void convert(final NBTTagCompound section, final int[] ids) {
		final byte[] blocks = section.getByteArray("Blocks");
		byte[] add = section.hasKey("Add", 7) ? section.getByteArray("Add") : null;
		for (int index = 0; index < blocks.length; index++) {
			final int shift = (index & 1) << 2;
			final int high = add == null ? 0 : add[index >> 1] >> shift & 15;
			final int id = ids[blocks[index] & 255 | high << 8];
			blocks[index] = (byte) id;
			if (id >> 8 != high) {
				if (add == null) {
					add = new byte[2048];
					section.setByteArray("Add", add);
				}
				add[index >> 1] = (byte) (add[index >> 1] & ~(15 << shift) | (id >> 8) << shift);
			}
		}
	}

	private Optional<int[]> load(final File directory) {
		final int[] ids = new int[4096];
		for (int id = 0; id < ids.length; id++) {
			ids[id] = id;
		}

		final File file = new File(directory, "level.dat");
		if (!file.isFile()) {
			return Optional.empty();
		}

		boolean identity = true;
		try (final InputStream stream = Files.newInputStream(file.toPath())) {
			final NBTTagList entries = CompressedStreamTools.readCompressed(stream).getCompoundTag("FML").getTagList("ItemData", 10);
			for (int index = 0; index < entries.tagCount(); index++) {
				final NBTTagCompound entry = entries.getCompoundTagAt(index);
				final String key = entry.getString("K");
				final int id = entry.getInteger("V");
				if (key.length() > 1 && key.charAt(0) == '\u0001' && id >= 0 && id < ids.length) {
					ids[id] = Math.max(0, GameData.getBlockRegistry().getId(key.substring(1)));
					identity &= ids[id] == id;
				}
			}
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the block ids of " + file + ": " + e.getMessage());
		}
		return identity ? Optional.empty() : Optional.of(ids);
	}

}