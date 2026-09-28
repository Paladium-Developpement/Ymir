package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;

import fr.paladium.ymir.lib.YmirWorldConfig;
import fr.paladium.ymir.lib.YmirWorldException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.DimensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldFiles {

	private static final String LEVEL = "level.dat";
	private static final String TRASH_PREFIX = ".ymir-trash-";
	private static final String[] CACHE_FIELDS = { "REGIONS_BY_FILE", "field_76553_a", "regionsByFilename" };
	private static final String[] COPY_EXCLUDES = { "session.lock", "uid.dat", "playerdata", "stats", "advancements" };

	public static File getContainer() {
		final WorldServer overworld = DimensionManager.getWorld(0);
		if (overworld == null) {
			throw new YmirWorldException("The main world is not loaded yet");
		}
		return overworld.getSaveHandler().getWorldDirectory();
	}

	public static File getDirectory(final String name) {
		return new File(YmirWorldFiles.getContainer(), name);
	}

	public static boolean exists(final String name) {
		return new File(YmirWorldFiles.getDirectory(name), YmirWorldFiles.LEVEL).exists();
	}

	public static void flush() {
		try {
			ThreadedFileIOBase.threadedIOInstance.waitForFinish();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static void prepare(final YmirWorldConfig config) {
		if (config.getTemplate() != null && !YmirWorldFiles.exists(config.getName())) {
			YmirWorldFiles.copy(config.getTemplate(), YmirWorldFiles.getDirectory(config.getName()));
		}

		if (config.isWorldGuard()) {
			YmirWorldGuard.prepare(config.getName());
		}
	}

	public static void copy(final File source, final File target) {
		if (!source.isDirectory()) {
			throw new YmirWorldException("The directory " + source.getAbsolutePath() + " does not exist");
		}

		try {
			FileUtils.copyDirectory(source, target, file -> {
				for (final String exclude : YmirWorldFiles.COPY_EXCLUDES) {
					if (exclude.equalsIgnoreCase(file.getName())) {
						return false;
					}
				}
				return true;
			});
		} catch (final IOException e) {
			throw new YmirWorldException("Unable to copy " + source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
		}
	}

	public static void reset(final File directory, final String name) {
		final File level = new File(directory, YmirWorldFiles.LEVEL);
		if (!level.exists()) {
			return;
		}

		try {
			final NBTTagCompound root;
			try (final InputStream input = new FileInputStream(level)) {
				root = CompressedStreamTools.readCompressed(input);
			}

			final NBTTagCompound data = root.getCompoundTag("Data");
			data.setInteger("dimension", 0);
			data.setString("LevelName", name);
			data.removeTag("Player");
			root.setTag("Data", data);

			try (final OutputStream output = new FileOutputStream(level)) {
				CompressedStreamTools.writeCompressed(root, output);
			}
		} catch (final IOException e) {
			throw new YmirWorldException("Unable to release the dimension identifier of the world " + name, e);
		}
	}

	public static void release(final File directory) {
		YmirWorldFiles.flush();

		final Map<File, RegionFile> regions = YmirWorldFiles.regions();
		if (regions == null) {
			return;
		}

		final Path root = directory.toPath().toAbsolutePath().normalize();
		synchronized (RegionFileCache.class) {
			final Iterator<Map.Entry<File, RegionFile>> iterator = regions.entrySet().iterator();
			while (iterator.hasNext()) {
				final Map.Entry<File, RegionFile> entry = iterator.next();
				if (!entry.getKey().toPath().toAbsolutePath().normalize().startsWith(root)) {
					continue;
				}

				try {
					entry.getValue().close();
				} catch (final IOException e) {
					System.err.println("[Ymir] Failed to close the region file " + entry.getKey().getAbsolutePath() + ".");
					e.printStackTrace();
				}
				iterator.remove();
			}
		}
	}

	public static boolean discard(final YmirWorldConfig config) {
		final List<File> directories = YmirWorldFiles.linked(config, config.getName());
		final File directory = YmirWorldFiles.getDirectory(config.getName());
		if (directory.exists()) {
			final File trash = new File(directory.getParentFile(), YmirWorldFiles.TRASH_PREFIX + config.getName() + "-" + System.currentTimeMillis());
			if (!directory.renameTo(trash)) {
				System.err.println("[Ymir] Failed to move the world directory " + directory.getAbsolutePath() + " to " + trash.getName() + ".");
				return false;
			}
			directories.add(trash);
		}

		new Thread(() -> directories.forEach(YmirWorldFiles::delete), "Ymir/Cleanup-" + config.getName()).start();
		return true;
	}

	public static void copyLinked(final YmirWorldConfig config, final String source, final String target) {
		final List<File> sources = YmirWorldFiles.linked(config, source);
		final List<File> targets = YmirWorldFiles.linked(config, target);
		for (int index = 0; index < sources.size(); index++) {
			if (sources.get(index).isDirectory() && !targets.get(index).exists()) {
				YmirWorldFiles.copy(sources.get(index), targets.get(index));
			}
		}
	}

	public static List<File> linked(final YmirWorldConfig config, final String name) {
		final List<File> directories = new ArrayList<>();
		for (final Function<String, File> resolver : config.getLinked()) {
			final File directory = resolver.apply(name);
			if (directory != null) {
				directories.add(directory);
			}
		}

		if (config.isWorldGuard()) {
			final File worldGuard = YmirWorldGuard.resolve(name);
			if (worldGuard != null) {
				directories.add(worldGuard);
			}
		}

		return directories;
	}

	private static void delete(final File directory) {
		if (!directory.exists()) {
			return;
		}

		try {
			FileUtils.deleteDirectory(directory);
		} catch (final IOException e) {
			System.err.println("[Ymir] Failed to delete the directory " + directory.getAbsolutePath() + ".");
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<File, RegionFile> regions() {
		for (final String name : YmirWorldFiles.CACHE_FIELDS) {
			try {
				final Field field = RegionFileCache.class.getDeclaredField(name);
				field.setAccessible(true);
				return (Map<File, RegionFile>) field.get(null);
			} catch (final ReflectiveOperationException e) {}
		}

		System.err.println("[Ymir] Unable to access the region file cache, region files stay open.");
		return null;
	}

}