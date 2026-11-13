package fr.paladium.ymir.internal.world.backend;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.World.Environment;

import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.FMLOutboundHandler.OutboundTarget;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import cpw.mods.fml.relauncher.Side;
import fr.paladium.ymir.internal.world.YmirWorldFiles;
import fr.paladium.ymir.internal.world.YmirWorldTeleporter;
import fr.paladium.ymir.internal.world.provider.YmirEndWorldProvider;
import fr.paladium.ymir.internal.world.provider.YmirNetherWorldProvider;
import fr.paladium.ymir.internal.world.provider.YmirSurfaceWorldProvider;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldConfig;
import fr.paladium.ymir.lib.YmirWorldException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.network.ForgeMessage;
import net.minecraftforge.event.world.WorldEvent;

public final class ForgeYmirWorldBackend implements IYmirWorldBackend {

	private static final ForgeYmirWorldBackend INSTANCE = new ForgeYmirWorldBackend();

	private final Map<Environment, Integer> providers = new EnumMap<>(Environment.class);

	private ForgeYmirWorldBackend() {
		for (final Environment environment : Environment.values()) {
			this.providers.put(environment, this.register(environment));
		}
	}

	public static ForgeYmirWorldBackend inst() {
		return ForgeYmirWorldBackend.INSTANCE;
	}

	@Override
	public WorldServer load(final YmirWorld world) {
		final YmirWorldConfig config = world.getConfig();
		final WorldType type = WorldType.parseWorldType(config.getType().getName());
		if (type == null) {
			throw new YmirWorldException("The world type " + config.getType().getName() + " is not registered");
		}

		final MinecraftServer server = MinecraftServer.getServer();
		final int dimensionId = DimensionManager.getNextFreeDimId();
		DimensionManager.registerDimension(dimensionId, this.providers.get(config.getEnvironment()));

		try {
			final WorldSettings settings = new WorldSettings(config.getSeed(), server.getGameType(), config.isStructures(), server.isHardcore(), type);
			final WorldServer handle = new WorldServer(server, new AnvilSaveHandler(YmirWorldFiles.getContainer(), world.getName(), true), world.getName(), dimensionId, settings, server.theProfiler);
			handle.addWorldAccess(new WorldManager(server, handle));
			handle.getWorldInfo().setGameType(server.getGameType());
			handle.difficultySetting = server.func_147135_j();
			handle.levelSaving = !config.isAutoSave();
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(handle));
			return handle;
		} catch (final RuntimeException e) {
			if (DimensionManager.getWorld(dimensionId) != null) {
				DimensionManager.setWorld(dimensionId, null);
			}

			DimensionManager.unregisterDimension(dimensionId);
			throw new YmirWorldException("Unable to create the world " + world.getName(), e);
		}
	}

	@Override
	public boolean unload(final YmirWorld world, final boolean save) {
		if (save) {
			this.save(world);
		}

		final WorldServer handle = world.getHandle();
		MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(handle));
		DimensionManager.setWorld(handle.provider.dimensionId, null);
		return true;
	}

	@Override
	public void save(final YmirWorld world) {
		final WorldServer handle = world.getHandle();
		final boolean levelSaving = handle.levelSaving;
		handle.levelSaving = false;
		try {
			handle.saveAllChunks(true, null);
		} catch (final MinecraftException e) {
			throw new YmirWorldException("Unable to save the world " + world.getName(), e);
		} finally {
			handle.levelSaving = levelSaving;
		}
	}

	@Override
	public void connect(final NetworkManager manager, final EntityPlayerMP player) {
		final NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
		for (final WorldServer handle : DimensionManager.getWorlds()) {
			Ymir.get(handle).ifPresent(world -> this.sync(OutboundTarget.DISPATCHER, dispatcher, handle.provider.dimensionId, world.getConfig().getEnvironment()));
		}

		final NBTTagCompound data = player.mcServer.getConfigurationManager().getPlayerNBT(player);
		final int dimensionId = data == null ? 0 : data.getInteger("Dimension");
		if (!DimensionManager.isDimensionRegistered(dimensionId)) {
			this.sync(OutboundTarget.DISPATCHER, dispatcher, dimensionId, Environment.NORMAL);
		}
	}

	@Override
	public World getBukkitWorld(final YmirWorld world) {
		return null;
	}

	@Override
	public void setSpawn(final YmirWorld world, final int x, final int y, final int z) {
		world.getHandle().setSpawnLocation(x, y, z);
	}

	@Override
	public boolean teleport(final YmirWorld world, final EntityPlayerMP player, final double x, final double y, final double z, final float yaw, final float pitch) {
		if (player.ridingEntity != null) {
			player.mountEntity(null);
		}

		final WorldServer handle = world.getHandle();
		final int dimensionId = handle.provider.dimensionId;
		if (player.dimension != dimensionId) {
			this.sync(OutboundTarget.PLAYER, player, dimensionId, world.getConfig().getEnvironment());
			player.mcServer.getConfigurationManager().transferPlayerToDimension(player, dimensionId, new YmirWorldTeleporter(handle, x, y, z, yaw, pitch));
		}

		player.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, pitch);
		return true;
	}

	private void sync(final OutboundTarget target, final Object receiver, final int dimensionId, final Environment environment) {
		final FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel("FORGE", Side.SERVER);
		channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(target);
		channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(receiver);
		channel.writeOutbound(new ForgeMessage.DimensionRegisterMessage(dimensionId, environment == Environment.NETHER ? -1 : environment == Environment.THE_END ? 1 : 0));
	}

	private int register(final Environment environment) {
		final Class<? extends WorldProvider> provider = environment == Environment.NETHER ? YmirNetherWorldProvider.class : environment == Environment.THE_END ? YmirEndWorldProvider.class : YmirSurfaceWorldProvider.class;
		int type = 1000;
		while (!DimensionManager.registerProviderType(type, provider, true)) {
			type++;
		}
		return type;
	}

}