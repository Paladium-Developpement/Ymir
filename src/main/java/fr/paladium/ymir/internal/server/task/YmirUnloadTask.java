package fr.paladium.ymir.internal.server.task;

import java.time.Duration;

import fr.paladium.palaforgeutils.lib.scheduler.MinecraftThread;
import fr.paladium.palaforgeutils.lib.task.ATask;
import fr.paladium.palaforgeutils.lib.task.Schedule;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldException;

@Schedule(every = "1s")
public class YmirUnloadTask extends ATask {

	public YmirUnloadTask() {
		super("Ymir/Unload");
	}

	@Override
	public void run() {
		MinecraftThread.execute(YmirUnloadTask::tick);
	}

	private static void tick() {
		final long now = System.currentTimeMillis();
		for (final YmirWorld world : Ymir.getWorlds()) {
			final Duration unloadAfter = world.getConfig().getUnloadAfter();
			final Duration deleteAfter = world.getConfig().getDeleteAfter();
			if (unloadAfter == null && deleteAfter == null || !world.isLoaded()) {
				continue;
			}

			if (!world.getPlayers().isEmpty()) {
				world.keepAlive();
				continue;
			}

			final long empty = now - world.getEmptySince();
			if (deleteAfter != null && empty >= deleteAfter.toMillis()) {
				YmirUnloadTask.delete(world);
				continue;
			}

			if (unloadAfter != null && empty >= unloadAfter.toMillis()) {
				world.unload();
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