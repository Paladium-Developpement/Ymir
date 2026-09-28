package fr.paladium.ymir.internal.server.task;

import java.time.Duration;

import fr.paladium.palaforgeutils.lib.scheduler.MinecraftThread;
import fr.paladium.palaforgeutils.lib.task.ATask;
import fr.paladium.palaforgeutils.lib.task.Schedule;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;

@Schedule(every = "10s")
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
			if (unloadAfter == null || !world.isLoaded()) {
				continue;
			}

			if (!world.getPlayers().isEmpty()) {
				world.keepAlive();
				continue;
			}

			if (now - world.getEmptySince() >= unloadAfter.toMillis()) {
				world.unload();
			}
		}
	}

}