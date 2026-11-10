package fr.paladium.ymir.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;

import fr.paladium.ymir.internal.world.YmirWorldDimensions;
import fr.paladium.ymir.internal.world.YmirWorldFiles;
import fr.paladium.ymir.internal.world.backend.YmirWorldBackend;
import fr.paladium.ymir.lib.event.YmirWorldEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

@Getter
public final class YmirWorld {

	private final YmirWorldConfig config;

	private long        emptySince;
	private WorldServer handle;

	@Getter(AccessLevel.NONE) private boolean fresh;
	@Getter(AccessLevel.NONE) private boolean prepared;

	protected YmirWorld(final @NonNull YmirWorldConfig config) {
		this.config = config;
	}

	public boolean isLoaded() {
		if (this.handle == null) {
			return false;
		}

		final int dimensionId = this.handle.provider.dimensionId;
		if (DimensionManager.getWorld(dimensionId) == this.handle) {
			return true;
		}

		this.handle = null;
		Ymir.detach(dimensionId, this);
		YmirWorldDimensions.unregister(dimensionId);
		return false;
	}

	public @NonNull String getName() {
		return this.config.getName();
	}

	public @NonNull File getDirectory() {
		return YmirWorldFiles.getDirectory(this.getName());
	}

	public int getDimensionId() {
		return this.require().handle.provider.dimensionId;
	}

	public World getBukkitWorld() {
		return this.isLoaded() ? YmirWorldBackend.get().getBukkitWorld(this) : null;
	}

	public @NonNull YmirWorld keepAlive() {
		this.emptySince = System.currentTimeMillis();
		return this;
	}

	public @NonNull List<EntityPlayerMP> getPlayers() {
		final List<EntityPlayerMP> players = new ArrayList<>();
		if (!this.isLoaded()) {
			return players;
		}

		for (final Object entity : this.handle.playerEntities) {
			if (entity instanceof EntityPlayerMP) {
				players.add((EntityPlayerMP) entity);
			}
		}

		return players;
	}

	public @NonNull YmirWorld save() {
		if (this.isLoaded()) {
			YmirWorldBackend.get().save(this);
		}
		return this;
	}

	public @NonNull YmirWorld load() {
		if (this.isLoaded()) {
			return this;
		}

		if (this.getName().equals(YmirWorldFiles.getMainWorldName())) {
			throw new YmirWorldException("The main world " + this.getName() + " cannot be managed by Ymir");
		}

		this.prepare();
		YmirWorldFiles.reset(this.getDirectory(), this.getName());

		this.handle = YmirWorldBackend.get().load(this);
		this.config.getGamerules().forEach(this::setGamerule);
		this.keepAlive();
		Ymir.attach(this);

		if (this.fresh) {
			this.fresh = false;
			this.platform();
			MinecraftForge.EVENT_BUS.post(new YmirWorldEvent.Create(this));
		}

		MinecraftForge.EVENT_BUS.post(new YmirWorldEvent.Load(this));
		return this;
	}

	public boolean unload() {
		return this.unload(true);
	}

	public boolean unload(final boolean save) {
		if (!this.isLoaded()) {
			return true;
		}

		if (!this.getPlayers().isEmpty()) {
			return false;
		}

		final int dimensionId = this.getDimensionId();
		if (!YmirWorldBackend.get().unload(this, save)) {
			return false;
		}

		MinecraftForge.EVENT_BUS.post(new YmirWorldEvent.Unload(this));
		this.handle = null;
		Ymir.detach(dimensionId, this);

		YmirWorldFiles.release(this.getDirectory());
		YmirWorldDimensions.unregister(dimensionId);
		return true;
	}

	public void delete() {
		if (!this.unload(false)) {
			throw new YmirWorldException("The world " + this.getName() + " still contains players");
		}

		MinecraftForge.EVENT_BUS.post(new YmirWorldEvent.Delete(this));
		Ymir.forget(this);

		if (!YmirWorldFiles.discard(this.config)) {
			throw new YmirWorldException("Unable to move the files of the world " + this.getName());
		}
	}

	public @NonNull CompletableFuture<YmirWorld> copy(final @NonNull String name) {
		return Ymir.copy(this.config, this.config.copy(name));
	}

	public @NonNull CompletableFuture<YmirWorld> copy(final @NonNull YmirWorldConfig config) {
		return Ymir.copy(this.config, config);
	}

	public boolean teleport(final @NonNull EntityPlayerMP player) {
		final ChunkCoordinates spawn = this.load().handle.getSpawnPoint();
		return this.teleport(player, spawn.posX + 0.5D, spawn.posY, spawn.posZ + 0.5D, player.rotationYaw, player.rotationPitch);
	}

	public boolean teleport(final @NonNull EntityPlayerMP player, final double x, final double y, final double z, final float yaw, final float pitch) {
		return YmirWorldBackend.get().teleport(this.load(), player, x, y, z, yaw, pitch);
	}

	public @NonNull YmirWorld setSpawn(final int x, final int y, final int z) {
		YmirWorldBackend.get().setSpawn(this.require(), x, y, z);
		return this;
	}

	public @NonNull YmirWorld setGamerule(final @NonNull String key, final boolean value) {
		return this.setGamerule(key, String.valueOf(value));
	}

	public @NonNull YmirWorld setGamerule(final @NonNull String key, final @NonNull String value) {
		this.require().handle.getGameRules().setOrCreateGameRule(key, value);
		return this;
	}

	@NonNull YmirWorld prepare() {
		if (!this.prepared) {
			this.fresh = !Ymir.exists(this.getName());
			YmirWorldFiles.prepare(this.config);
			this.prepared = true;
		}
		return this;
	}

	private @NonNull YmirWorld require() {
		if (!this.isLoaded()) {
			throw new YmirWorldException("The world " + this.getName() + " is not loaded");
		}
		return this;
	}

	private void platform() {
		if (this.config.getPlatform() <= 0) {
			return;
		}

		final ChunkCoordinates spawn = this.handle.getSpawnPoint();
		for (int x = -this.config.getPlatform(); x <= this.config.getPlatform(); x++) {
			for (int z = -this.config.getPlatform(); z <= this.config.getPlatform(); z++) {
				this.handle.setBlock(spawn.posX + x, spawn.posY - 1, spawn.posZ + z, this.config.getPlatformBlock());
			}
		}
	}

}