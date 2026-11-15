package fr.paladium.ymir.internal.world;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldLoads {

	private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

	public static void execute(final Runnable load) {
		YmirWorldLoads.QUEUE.add(load);
	}

	public static void tick() {
		final Runnable load = YmirWorldLoads.QUEUE.poll();
		if (load != null) {
			load.run();
		}
	}

}