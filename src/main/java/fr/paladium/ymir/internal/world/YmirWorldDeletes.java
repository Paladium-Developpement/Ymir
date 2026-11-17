package fr.paladium.ymir.internal.world;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldDeletes {

	private static final Set<YmirWorld> QUEUE = new LinkedHashSet<>();

	public static void execute(final YmirWorld world) {
		YmirWorldDeletes.QUEUE.add(world);
	}

	public static void tick() {
		for (int deleted = 0; deleted < 2 && !YmirWorldDeletes.QUEUE.isEmpty(); deleted++) {
			final Iterator<YmirWorld> iterator = YmirWorldDeletes.QUEUE.iterator();
			final YmirWorld world = iterator.next();
			iterator.remove();
			if (world.getPlayers().isEmpty() && !YmirWorldChunks.isPreloading(world)) {
				YmirWorldDeletes.delete(world);
			}
		}
	}

	private static void delete(final YmirWorld world) {
		try {
			world.delete();
		} catch (final YmirWorldException e) {
			System.err.println("[Ymir] Failed to delete the world " + world.getName() + ".");
			e.printStackTrace();
		}
	}

}