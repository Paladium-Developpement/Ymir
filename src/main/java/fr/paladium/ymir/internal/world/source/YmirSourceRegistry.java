package fr.paladium.ymir.internal.world.source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.registry.GameData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirSourceRegistry {

	private static final YmirSourceRegistry INSTANCE = new YmirSourceRegistry();

	private final Map<File, int[]> blocks = new ConcurrentHashMap<>();

	public static YmirSourceRegistry inst() {
		return YmirSourceRegistry.INSTANCE;
	}

	public int[] getBlocks(final File directory) {
		return this.blocks.computeIfAbsent(directory, this::load);
	}

	private int[] load(final File directory) {
		final int[] ids = new int[4096];
		for (int id = 0; id < ids.length; id++) {
			ids[id] = id;
		}

		final File file = new File(directory, "level.dat");
		if (!file.isFile()) {
			return ids;
		}

		try (final InputStream stream = Files.newInputStream(file.toPath())) {
			final NBTTagList entries = CompressedStreamTools.readCompressed(stream).getCompoundTag("FML").getTagList("ItemData", 10);
			for (int index = 0; index < entries.tagCount(); index++) {
				final NBTTagCompound entry = entries.getCompoundTagAt(index);
				final String key = entry.getString("K");
				final int id = entry.getInteger("V");
				if (key.length() > 1 && key.charAt(0) == '' && id >= 0 && id < ids.length) {
					ids[id] = Math.max(0, GameData.getBlockRegistry().getId(key.substring(1)));
				}
			}
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the block ids of " + file + ": " + e.getMessage());
		}
		return ids;
	}

}