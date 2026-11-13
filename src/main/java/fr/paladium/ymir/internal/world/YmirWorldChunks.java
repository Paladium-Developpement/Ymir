package fr.paladium.ymir.internal.world;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import fr.paladium.ymir.lib.YmirWorld;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.ChunkCoordIntPair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldChunks {

	private static final Map<YmirWorld, Queue<Runnable>> QUEUES = new LinkedHashMap<>();

	public static CompletableFuture<YmirWorld> preload(final YmirWorld world, final Collection<ChunkCoordIntPair> chunks) {
		final CompletableFuture<YmirWorld> future = new CompletableFuture<>();
		if (chunks.isEmpty()) {
			future.complete(world);
			return future;
		}

		final AtomicInteger remaining = new AtomicInteger(chunks.size());
		final Runnable loaded = () -> {
			if (remaining.decrementAndGet() == 0) {
				future.complete(world);
			}
		};

		final Queue<Runnable> queue = YmirWorldChunks.QUEUES.computeIfAbsent(world, key -> new ArrayDeque<>());
		for (final ChunkCoordIntPair chunk : chunks) {
			queue.add(() -> {
				if (!world.isLoaded()) {
					loaded.run();
					return;
				}

				world.getHandle().theChunkProviderServer.loadChunk(chunk.chunkXPos, chunk.chunkZPos, loaded);
			});
		}
		return future;
	}

	public static void tick() {
		int budget = 16;
		while (budget > 0 && !YmirWorldChunks.QUEUES.isEmpty()) {
			final Iterator<Queue<Runnable>> iterator = YmirWorldChunks.QUEUES.values().iterator();
			while (budget > 0 && iterator.hasNext()) {
				final Queue<Runnable> queue = iterator.next();
				queue.poll().run();
				budget--;
				if (queue.isEmpty()) {
					iterator.remove();
				}
			}
		}
	}

}