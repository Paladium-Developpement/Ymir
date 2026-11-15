package fr.paladium.ymir.internal.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import fr.paladium.ymir.internal.world.source.YmirSourceChunkProvider;
import fr.paladium.ymir.lib.YmirWorld;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.IChunkProvider;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldChunks {

	private static final Map<YmirWorld, Queue<BooleanSupplier>> QUEUES = new LinkedHashMap<>();

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

		final IChunkProvider provider = world.getHandle().theChunkProviderServer.currentChunkProvider;
		final YmirSourceChunkProvider source = provider instanceof YmirSourceChunkProvider ? (YmirSourceChunkProvider) provider : null;
		final Queue<BooleanSupplier> queue = YmirWorldChunks.QUEUES.computeIfAbsent(world, key -> new ArrayDeque<>());
		for (final ChunkCoordIntPair chunk : chunks) {
			if (source != null) {
				source.prefetch(chunk.chunkXPos, chunk.chunkZPos);
			}
			queue.add(() -> YmirWorldChunks.load(world, source, chunk, loaded));
		}
		return future;
	}

	public static void tick() {
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5L);
		int budget = 16;
		boolean progress = true;
		while (progress && budget > 0 && System.nanoTime() < deadline) {
			progress = false;
			for (final Queue<BooleanSupplier> queue : new ArrayList<>(YmirWorldChunks.QUEUES.values())) {
				if (budget <= 0 || System.nanoTime() >= deadline) {
					break;
				}

				if (queue.isEmpty() || !queue.peek().getAsBoolean()) {
					continue;
				}

				queue.poll();
				budget--;
				progress = true;
			}
			YmirWorldChunks.QUEUES.values().removeIf(Queue::isEmpty);
		}
	}

	private static boolean load(final YmirWorld world, final YmirSourceChunkProvider source, final ChunkCoordIntPair chunk, final Runnable loaded) {
		if (!world.isLoaded()) {
			loaded.run();
			return true;
		}

		if (source == null) {
			world.getHandle().theChunkProviderServer.loadChunk(chunk.chunkXPos, chunk.chunkZPos, loaded);
			return true;
		}

		if (!source.isPrefetched(chunk.chunkXPos, chunk.chunkZPos)) {
			return false;
		}

		world.getHandle().theChunkProviderServer.loadChunk(chunk.chunkXPos, chunk.chunkZPos);
		loaded.run();
		return true;
	}

}