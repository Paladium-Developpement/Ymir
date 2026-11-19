package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.paladium.ymir.internal.world.region.ReadOnlyYmirRegion;
import fr.paladium.ymir.lib.YmirWorldBox;
import fr.paladium.ymir.lib.YmirWorldConfig;
import fr.paladium.ymir.lib.YmirWorldSource;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldZones {

	private static final Pattern                         REGION = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
	private static final Map<String, List<YmirWorldBox>> CACHE  = new ConcurrentHashMap<>();
	private static final Method                          ADD    = YmirWorldZones.find("addChunkZone");
	private static final Method                          REMOVE = YmirWorldZones.find("removeChunkZone");

	private static boolean warned;

	public static List<YmirWorldBox> resolve(final YmirWorldConfig config) {
		if (!config.getBounds().isEmpty()) {
			return config.getBounds();
		}

		if (config.getTemplate() != null && config.getTemplate().isDirectory()) {
			return YmirWorldZones.scan(new File(config.getTemplate(), "region"));
		}

		final YmirWorldSource source = config.getSource();
		if (source != null && source.getMinChunkX() != Integer.MIN_VALUE && source.getMinChunkZ() != Integer.MIN_VALUE && source.getMaxChunkX() != Integer.MAX_VALUE && source.getMaxChunkZ() != Integer.MAX_VALUE) {
			return Collections.singletonList(YmirWorldBox.of(source.getMinChunkX(), source.getMinChunkZ(), source.getMaxChunkX(), source.getMaxChunkZ()).expand(1));
		}

		return Collections.emptyList();
	}

	public static void add(final World world, final YmirWorldBox box) {
		YmirWorldZones.invoke(YmirWorldZones.ADD, world, box);
	}

	public static void remove(final World world, final YmirWorldBox box) {
		YmirWorldZones.invoke(YmirWorldZones.REMOVE, world, box);
	}

	private static List<YmirWorldBox> scan(final File directory) {
		final File[] regions = directory.listFiles((parent, name) -> YmirWorldZones.REGION.matcher(name).matches());
		if (regions == null || regions.length == 0) {
			return Collections.emptyList();
		}

		Arrays.sort(regions);
		final StringBuilder key = new StringBuilder(directory.getAbsolutePath());
		for (final File region : regions) {
			key.append('|').append(region.getName()).append(':').append(region.length()).append(':').append(region.lastModified());
		}

		return YmirWorldZones.CACHE.computeIfAbsent(key.toString(), fingerprint -> YmirWorldZones.cover(YmirWorldZones.content(regions)));
	}

	private static Set<Long> content(final File[] regions) {
		final Set<Long> chunks = new HashSet<>();
		for (final File region : regions) {
			final Matcher matcher = YmirWorldZones.REGION.matcher(region.getName());
			if (!matcher.matches()) {
				continue;
			}

			final int regionX = Integer.parseInt(matcher.group(1));
			final int regionZ = Integer.parseInt(matcher.group(2));
			try (final ReadOnlyYmirRegion file = new ReadOnlyYmirRegion(region)) {
				for (int x = 0; x < 32; x++) {
					for (int z = 0; z < 32; z++) {
						if (!file.exists(x, z)) {
							continue;
						}

						final int chunkX = regionX * 32 + x;
						final int chunkZ = regionZ * 32 + z;
						final Optional<NBTTagCompound> chunk = file.read(x, z);
						if (chunk.isPresent() && YmirWorldZones.isFilled(chunk.get().getCompoundTag("Level"))) {
							chunks.add(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
						}
					}
				}
			} catch (final IOException e) {
				System.err.println("[Ymir] Unable to read the region " + region.getAbsolutePath() + " to compute the world zone: " + e.getMessage());
				return Collections.emptySet();
			}
		}
		return chunks;
	}

	private static boolean isFilled(final NBTTagCompound level) {
		if (level.getTagList("TileEntities", 10).tagCount() > 0 || level.getTagList("Entities", 10).tagCount() > 0) {
			return true;
		}

		final NBTTagList sections = level.getTagList("Sections", 10);
		for (int i = 0; i < sections.tagCount(); i++) {
			final NBTTagCompound section = sections.getCompoundTagAt(i);
			if (YmirWorldZones.isFilled(section.getByteArray("Blocks")) || YmirWorldZones.isFilled(section.getByteArray("Add"))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isFilled(final byte[] blocks) {
		for (final byte block : blocks) {
			if (block != 0) {
				return true;
			}
		}
		return false;
	}

	private static List<YmirWorldBox> cover(final Set<Long> content) {
		if (content.isEmpty()) {
			return Collections.emptyList();
		}

		final Set<Long> zone = new HashSet<>();
		for (final long chunk : content) {
			final int chunkX = (int) chunk;
			final int chunkZ = (int) (chunk >> 32);
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					zone.add(ChunkCoordIntPair.chunkXZ2Int(chunkX + x, chunkZ + z));
				}
			}
		}

		final List<Long> chunks = new ArrayList<>(zone);
		chunks.sort((first, second) -> {
			final int byZ = Integer.compare((int) (first >> 32), (int) (second >> 32));
			return byZ != 0 ? byZ : Integer.compare((int) (long) first, (int) (long) second);
		});

		final List<YmirWorldBox> boxes = new ArrayList<>();
		final Set<Long> covered = new HashSet<>();
		for (final long chunk : chunks) {
			if (covered.contains(chunk)) {
				continue;
			}

			final int minX = (int) chunk;
			final int minZ = (int) (chunk >> 32);
			int maxX = minX;
			while (YmirWorldZones.isFree(zone, covered, maxX + 1, minZ)) {
				maxX++;
			}

			int maxZ = minZ;
			while (YmirWorldZones.isFree(zone, covered, minX, maxX, maxZ + 1)) {
				maxZ++;
			}

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					covered.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
				}
			}
			boxes.add(YmirWorldBox.of(minX, minZ, maxX, maxZ));
		}
		return Collections.unmodifiableList(boxes);
	}

	private static boolean isFree(final Set<Long> zone, final Set<Long> covered, final int chunkX, final int chunkZ) {
		final long key = ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ);
		return zone.contains(key) && !covered.contains(key);
	}

	private static boolean isFree(final Set<Long> zone, final Set<Long> covered, final int minX, final int maxX, final int chunkZ) {
		for (int x = minX; x <= maxX; x++) {
			if (!YmirWorldZones.isFree(zone, covered, x, chunkZ)) {
				return false;
			}
		}
		return true;
	}

	private static void invoke(final Method method, final World world, final YmirWorldBox box) {
		if (method == null) {
			if (!YmirWorldZones.warned) {
				YmirWorldZones.warned = true;
				System.err.println("[Ymir] The server does not support world zones, Ymir worlds load every chunk in view.");
			}
			return;
		}

		try {
			method.invoke(world, box.getMinChunkX(), box.getMinChunkZ(), box.getMaxChunkX(), box.getMaxChunkZ());
		} catch (final ReflectiveOperationException e) {
			System.err.println("[Ymir] Unable to update the zone of the world " + world.getWorldInfo().getWorldName() + ": " + e.getMessage());
		}
	}

	private static Method find(final String name) {
		try {
			return World.class.getMethod(name, int.class, int.class, int.class, int.class);
		} catch (final NoSuchMethodException e) {
			return null;
		}
	}

}