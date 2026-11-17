package fr.paladium.ymir.internal.world;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldLoads {

	private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
	private static final ExecutorService PREPARES = Executors.newFixedThreadPool(4, runnable -> {
		final Thread thread = new Thread(runnable, "Ymir/Prepare");
		thread.setDaemon(true);
		return thread;
	});

	public static void prepare(final Runnable task) {
		YmirWorldLoads.PREPARES.execute(task);
	}

	public static void execute(final Runnable load) {
		YmirWorldLoads.QUEUE.add(load);
	}

	public static void tick() {
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(12L);
		Runnable load = YmirWorldLoads.QUEUE.poll();
		while (load != null) {
			load.run();
			load = System.nanoTime() < deadline ? YmirWorldLoads.QUEUE.poll() : null;
		}
	}

}