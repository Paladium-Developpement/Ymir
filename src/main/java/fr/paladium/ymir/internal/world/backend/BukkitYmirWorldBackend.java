package fr.paladium.ymir.internal.world.backend;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import fr.paladium.ymir.internal.world.YmirWorldDimensions;
import fr.paladium.ymir.internal.world.provider.YmirSkylessWorldProvider;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldConfig;
import fr.paladium.ymir.lib.YmirWorldException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.WorldServer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BukkitYmirWorldBackend implements IYmirWorldBackend {

	private static final BukkitYmirWorldBackend INSTANCE = new BukkitYmirWorldBackend();

	private final Method spawnLocation = this.findSpawnLocation();

	private boolean skylessWarned;
	private boolean spawnAnglesWarned;

	public static BukkitYmirWorldBackend inst() {
		return BukkitYmirWorldBackend.INSTANCE;
	}

	@Override
	public WorldServer load(final YmirWorld world) {
		final World bukkitWorld = Bukkit.createWorld(this.creator(world.getConfig()));
		if (bukkitWorld == null) {
			throw new YmirWorldException("Bukkit refused to create the world " + world.getName());
		}

		final WorldServer handle = YmirWorldDimensions.find(world.getName());
		if (handle == null) {
			throw new YmirWorldException("Unable to find the world server of " + world.getName());
		}

		if (bukkitWorld.getKeepSpawnInMemory() != world.getConfig().isKeepSpawnInMemory()) {
			bukkitWorld.setKeepSpawnInMemory(world.getConfig().isKeepSpawnInMemory());
		}
		bukkitWorld.setAutoSave(world.getConfig().isAutoSave());

		return handle;
	}

	@Override
	public boolean unload(final YmirWorld world, final boolean save) {
		final World bukkitWorld = this.getBukkitWorld(world);
		return bukkitWorld != null && Bukkit.unloadWorld(bukkitWorld, save);
	}

	@Override
	public void save(final YmirWorld world) {
		this.getBukkitWorld(world).save();
	}

	@Override
	public void connect(final NetworkManager manager, final EntityPlayerMP player) {}

	@Override
	public World getBukkitWorld(final YmirWorld world) {
		return Bukkit.getWorld(world.getName());
	}

	@Override
	public float getSpawnYaw(final YmirWorld world) {
		return this.getBukkitWorld(world).getSpawnLocation().getYaw();
	}

	@Override
	public float getSpawnPitch(final YmirWorld world) {
		return this.getBukkitWorld(world).getSpawnLocation().getPitch();
	}

	@Override
	public void setSpawn(final YmirWorld world, final int x, final int y, final int z) {
		this.getBukkitWorld(world).setSpawnLocation(x, y, z);
	}

	@Override
	public void setSpawn(final YmirWorld world, final int x, final int y, final int z, final float yaw, final float pitch) {
		final World bukkitWorld = this.getBukkitWorld(world);
		if (this.spawnLocation == null) {
			if (!this.spawnAnglesWarned) {
				this.spawnAnglesWarned = true;
				System.err.println("[Ymir] The server does not support world spawn angles, Ymir world spawns keep their orientation.");
			}
			bukkitWorld.setSpawnLocation(x, y, z);
			return;
		}

		try {
			this.spawnLocation.invoke(bukkitWorld, new Location(bukkitWorld, x, y, z, yaw, pitch));
		} catch (final ReflectiveOperationException e) {
			System.err.println("[Ymir] Unable to set the spawn of the world " + world.getName() + ": " + e.getMessage());
		}
	}

	@Override
	public boolean teleport(final YmirWorld world, final EntityPlayerMP player, final double x, final double y, final double z, final float yaw, final float pitch) {
		final Player bukkitPlayer = Bukkit.getPlayer(player.getUniqueID());
		if (bukkitPlayer == null) {
			return false;
		}

		return bukkitPlayer.teleport(new Location(this.getBukkitWorld(world), x, y, z, yaw, pitch));
	}

	@SuppressWarnings("deprecation")
	private WorldCreator creator(final YmirWorldConfig config) {
		final WorldType type = WorldType.getByName(config.getType().getName());
		if (type == null) {
			throw new YmirWorldException("The world type " + config.getType().getName() + " is not registered");
		}

		final WorldCreator creator = new WorldCreator(config.getName());
		creator.generateStructures(config.isStructures());
		final Environment skyless = config.isSky() ? null : Environment.getEnvironment(YmirSkylessWorldProvider.getType());
		if (!config.isSky() && skyless == null && !this.skylessWarned) {
			this.skylessWarned = true;
			System.err.println("[Ymir] The skyless world type is not registered in Bukkit, skyless worlds are created with a sky.");
		}

		creator.environment(skyless == null ? config.getEnvironment() : skyless);
		creator.seed(config.getSeed());
		creator.type(type);
		return creator;
	}

	private Method findSpawnLocation() {
		try {
			return World.class.getMethod("setSpawnLocation", Location.class);
		} catch (final NoSuchMethodException e) {
			return null;
		}
	}

}