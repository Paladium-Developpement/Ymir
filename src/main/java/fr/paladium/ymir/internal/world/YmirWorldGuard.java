package fr.paladium.ymir.internal.world;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.sk89q.worldguard.blacklist.target.TargetMatcherParseException;
import com.sk89q.worldguard.blacklist.target.TargetMatcherParser;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldGuard {

	private static final String WORLDS    = "worlds";
	private static final String BLACKLIST = "blacklist.txt";

	private static final Pattern DATA_VALUE = Pattern.compile("^([^:]+)(:[^:]+)$");

	private static final Map<String, List<String>> BLACKLISTS = new ConcurrentHashMap<>();

	public static File resolve(final String name) {
		try {
			return new File(WorldGuardPlugin.inst().getDataFolder(), YmirWorldGuard.WORLDS + File.separator + name);
		} catch (final NoClassDefFoundError | Exception e) {
			return null;
		}
	}

	public static void prepare(final String name) {
		final File target = YmirWorldGuard.resolve(name);
		if (target == null || target.exists()) {
			return;
		}

		final WorldServer overworld = DimensionManager.getWorld(0);
		final File source = overworld == null ? null : YmirWorldGuard.resolve(overworld.getWorldInfo().getWorldName());
		if (source == null || !source.isDirectory()) {
			return;
		}

		try {
			FileUtils.copyDirectory(source, target);
			YmirWorldGuard.compileBlacklist(new File(source, YmirWorldGuard.BLACKLIST), new File(target, YmirWorldGuard.BLACKLIST));
		} catch (final Exception e) {
			System.err.println("[Ymir] Failed to copy the WorldGuard configuration of " + name + ".");
			e.printStackTrace();
		}
	}

	private static void compileBlacklist(final File source, final File target) throws IOException {
		if (!source.isFile()) {
			return;
		}

		final String key = source.getAbsolutePath() + ':' + source.lastModified() + ':' + source.length();
		List<String> lines = YmirWorldGuard.BLACKLISTS.get(key);
		if (lines == null) {
			lines = new ArrayList<>();
			for (final String line : Files.readAllLines(source.toPath(), StandardCharsets.ISO_8859_1)) {
				lines.add(YmirWorldGuard.compileLine(line));
			}
			YmirWorldGuard.BLACKLISTS.clear();
			YmirWorldGuard.BLACKLISTS.put(key, lines);
		}
		Files.write(target.toPath(), lines, StandardCharsets.ISO_8859_1);
	}

	private static String compileLine(final String line) {
		final String trimmed = line.trim();
		if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
			return line;
		}

		final List<String> items = new ArrayList<>();
		for (final String item : trimmed.substring(1, trimmed.length() - 1).split(",")) {
			final String compiled = YmirWorldGuard.compileItem(item);
			if (compiled != null) {
				items.add(compiled);
			}
		}
		return "[" + (items.isEmpty() ? "-1" : String.join(",", items)) + "]";
	}

	private static String compileItem(final String item) {
		final String trimmed = item.trim();
		final Matcher matcher = YmirWorldGuard.DATA_VALUE.matcher(trimmed);
		final String type = matcher.matches() ? matcher.group(1).trim() : trimmed;
		final String data = matcher.matches() ? matcher.group(2) : "";
		if (type.isEmpty() || type.chars().allMatch(Character::isDigit)) {
			return item;
		}

		try {
			return new TargetMatcherParser().fromInput(type).getMatchedTypeId() + data;
		} catch (final TargetMatcherParseException | RuntimeException e) {
			return null;
		}
	}

}