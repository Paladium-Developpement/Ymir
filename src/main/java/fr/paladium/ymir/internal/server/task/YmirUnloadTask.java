package fr.paladium.ymir.internal.server.task;

import java.time.Duration;

import fr.paladium.palaforgeutils.lib.scheduler.MinecraftThread;
import fr.paladium.palaforgeutils.lib.task.ATask;
import fr.paladium.palaforgeutils.lib.task.Schedule;
import fr.paladium.ymir.internal.world.YmirWorldChunks;
import fr.paladium.ymir.internal.world.YmirWorldDeletes;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;

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

			if (!world.getPlayers().isEmpty() || YmirWorldChunks.isPreloading(world)) {
				world.keepAlive();
				continue;
			}

			final long empty = now - world.getEmptySince();
			if (deleteAfter != null && empty >= deleteAfter.toMillis()) {
				YmirWorldDeletes.execute(world);
				continue;
			}

			if (unloadAfter != null && empty >= unloadAfter.toMillis()) {
				world.unload();
			}
		}
	}

}