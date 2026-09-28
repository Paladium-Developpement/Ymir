package fr.paladium.ymir.lib;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.bukkit.World.Environment;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChunkCoordinates;

@Getter
@SuppressWarnings("unchecked")
public class YmirWorldConfig {

	public static final String NAME_ERROR = "A world name must contain between 1 and 64 letters, digits, hyphens or underscores";

	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

	private final String name;

	private long             seed = new Random().nextLong();
	private int              platform;
	private boolean          worldGuard;
	private boolean          autoSave = true;
	private boolean          structures = true;
	private boolean          keepSpawnInMemory;
	private File             template;
	private Duration         unloadAfter;
	private ChunkCoordinates spawn;
	private Block            platformBlock = Blocks.stone;
	private Environment      environment = Environment.NORMAL;
	private YmirWorldType    type = YmirWorldType.NORMAL;

	private final List<Function<String, File>> linked = new ArrayList<>();
	private final Map<String, String>          gamerules = new LinkedHashMap<>();

	protected YmirWorldConfig(final @NonNull String name) {
		this.name = name;
	}

	public static @NonNull YmirWorldConfig create(final @NonNull String name) {
		if (!YmirWorldConfig.NAME_PATTERN.matcher(name).matches()) {
			throw new YmirWorldException(YmirWorldConfig.NAME_ERROR);
		}

		return new YmirWorldConfig(name.toLowerCase());
	}

	public @NonNull YmirWorldConfig copy(final @NonNull String name) {
		final YmirWorldConfig config = YmirWorldConfig.create(name);
		config.seed = this.seed;
		config.type = this.type;
		config.spawn = this.spawn;
		config.platform = this.platform;
		config.autoSave = this.autoSave;
		config.worldGuard = this.worldGuard;
		config.structures = this.structures;
		config.unloadAfter = this.unloadAfter;
		config.environment = this.environment;
		config.platformBlock = this.platformBlock;
		config.keepSpawnInMemory = this.keepSpawnInMemory;
		config.linked.addAll(this.linked);
		config.gamerules.putAll(this.gamerules);
		return config;
	}

	public final @NonNull <C extends YmirWorldConfig> C seed(final long seed) {
		this.seed = seed;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C seed(final @NonNull String seed) {
		try {
			this.seed = Long.parseLong(seed);
		} catch (final NumberFormatException e) {
			this.seed = seed.hashCode();
		}
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C platform(final int radius) {
		this.platform = radius;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C autoSave(final boolean autoSave) {
		this.autoSave = autoSave;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C worldGuard(final boolean worldGuard) {
		this.worldGuard = worldGuard;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C structures(final boolean structures) {
		this.structures = structures;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C link(final @NonNull Function<String, File> resolver) {
		this.linked.add(resolver);
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C template(final @NonNull File template) {
		this.template = template;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C type(final @NonNull YmirWorldType type) {
		this.type = type;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C keepSpawnInMemory(final boolean keepSpawnInMemory) {
		this.keepSpawnInMemory = keepSpawnInMemory;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C unloadAfter(final @NonNull Duration unloadAfter) {
		this.unloadAfter = unloadAfter;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C environment(final @NonNull Environment environment) {
		this.environment = environment;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C spawn(final int x, final int y, final int z) {
		this.spawn = new ChunkCoordinates(x, y, z);
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C platform(final @NonNull Block block, final int radius) {
		this.platformBlock = block;
		this.platform = radius;
		return (C) this;
	}

	public final @NonNull <C extends YmirWorldConfig> C gamerule(final @NonNull String key, final boolean value) {
		return this.gamerule(key, String.valueOf(value));
	}

	public final @NonNull <C extends YmirWorldConfig> C gamerule(final @NonNull String key, final @NonNull String value) {
		this.gamerules.put(key, value);
		return (C) this;
	}

}