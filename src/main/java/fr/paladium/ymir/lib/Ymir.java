package fr.paladium.ymir.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import fr.paladium.palaforgeutils.lib.scheduler.MinecraftThread;
import fr.paladium.ymir.internal.world.YmirWorldFiles;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Ymir {

	private static final Map<String, YmirWorld>  WORLDS     = new ConcurrentHashMap<>();
	private static final Map<Integer, YmirWorld> DIMENSIONS = new ConcurrentHashMap<>();

	public static @NonNull File getContainer() {
		return YmirWorldFiles.getContainer();
	}

	public static boolean exists(final @NonNull String name) {
		return YmirWorldFiles.exists(name.toLowerCase());
	}

	public static @NonNull List<YmirWorld> getWorlds() {
		return new ArrayList<>(Ymir.WORLDS.values());
	}

	public static @NonNull Optional<YmirWorld> get(final @NonNull String name) {
		return Optional.ofNullable(Ymir.WORLDS.get(name.toLowerCase()));
	}

	public static @NonNull Optional<YmirWorld> get(final @NonNull World world) {
		return Optional.ofNullable(Ymir.DIMENSIONS.get(world.provider.dimensionId));
	}

	public static @NonNull Optional<YmirWorld> get(final @NonNull Entity entity) {
		return entity.worldObj == null ? Optional.empty() : Ymir.get(entity.worldObj);
	}

	public static @NonNull YmirWorld create(final @NonNull YmirWorldConfig config) {
		return Ymir.of(config).load();
	}

	public static @NonNull CompletableFuture<YmirWorld> createAsync(final @NonNull YmirWorldConfig config) {
		final YmirWorld world = Ymir.of(config);
		return CompletableFuture.supplyAsync(world::prepare).thenApplyAsync(YmirWorld::load, MinecraftThread::execute).thenCompose(YmirWorld::preloadSpawn);
	}

	public static void delete(final @NonNull YmirWorldConfig config) {
		Ymir.of(config).delete();
	}

	public static @NonNull CompletableFuture<YmirWorld> copy(final @NonNull YmirWorldConfig source, final @NonNull YmirWorldConfig target) {
		if (Ymir.exists(target.getName()) || Ymir.get(target.getName()).isPresent()) {
			throw new YmirWorldException("The world " + target.getName() + " already exists");
		}

		final Optional<WorldServer> loaded = Ymir.get(source.getName()).filter(YmirWorld::isLoaded).map(YmirWorld::save).map(YmirWorld::getHandle);
		final boolean levelSaving = loaded.map(handle -> handle.levelSaving).orElse(false);
		loaded.ifPresent(handle -> handle.levelSaving = true);

		final File from = YmirWorldFiles.getDirectory(source.getName());
		final File to = YmirWorldFiles.getDirectory(target.getName());
		return CompletableFuture.supplyAsync(() -> {
			YmirWorldFiles.flush();
			YmirWorldFiles.copy(from, to);
			YmirWorldFiles.reset(to, target);
			YmirWorldFiles.copyLinked(source, source.getName(), target.getName());
			return target;
		}).whenCompleteAsync((config, error) -> loaded.ifPresent(handle -> handle.levelSaving = levelSaving), MinecraftThread::execute).thenApplyAsync(Ymir::create, MinecraftThread::execute);
	}

	public static void unloadAll() {
		Ymir.getWorlds().forEach(YmirWorld::unload);
	}

	static void attach(final YmirWorld world) {
		Ymir.DIMENSIONS.put(world.getDimensionId(), world);
	}

	static void detach(final int dimensionId, final YmirWorld world) {
		Ymir.DIMENSIONS.remove(dimensionId, world);
	}

	static void forget(final YmirWorld world) {
		Ymir.WORLDS.remove(world.getName());
	}

	private static YmirWorld of(final YmirWorldConfig config) {
		final YmirWorld known = Ymir.WORLDS.get(config.getName());
		if (known != null) {
			return known;
		}

		final YmirWorld world = new YmirWorld(config);
		Ymir.WORLDS.put(config.getName(), world);
		return world;
	}

}