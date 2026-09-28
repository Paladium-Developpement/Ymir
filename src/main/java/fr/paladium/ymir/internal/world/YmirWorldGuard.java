package fr.paladium.ymir.internal.world;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.bukkit.World;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldGuard {

	private static final String WORLDS = "worlds";

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
		} catch (final Exception e) {
			System.err.println("[Ymir] Failed to copy the WorldGuard configuration of " + name + ".");
			e.printStackTrace();
		}
	}

	public static void load(final World world) {
		try {
			WorldGuardPlugin.inst().getRegionContainer().get(world).load();
		} catch (final NoClassDefFoundError | Exception e) {
			System.err.println("[Ymir] Failed to load the WorldGuard regions of " + world.getName() + ".");
		}
	}

}