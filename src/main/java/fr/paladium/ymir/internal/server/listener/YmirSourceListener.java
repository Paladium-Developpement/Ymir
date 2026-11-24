package fr.paladium.ymir.internal.server.listener;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import fr.paladium.ymir.internal.world.YmirBlockIdRegistry;
import fr.paladium.ymir.internal.world.YmirChunkLoader;
import fr.paladium.ymir.internal.world.YmirTemplateChunkProvider;
import fr.paladium.ymir.internal.world.YmirWorldFiles;
import fr.paladium.ymir.internal.world.YmirWorldZones;
import fr.paladium.ymir.internal.world.source.YmirSourceChunkProvider;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldBox;
import fr.paladium.ymir.lib.YmirWorldConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.event.entity.EntityEvent.EnteringChunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class YmirSourceListener {

	private final Field keepSpawnInMemory = this.findWorldField("keepSpawnInMemory");
	private final Field unloadSpawnChunks = this.findWorldField("unloadSpawnChunks");
	private final Field viewRadius = ReflectionHelper.findField(PlayerManager.class, "field_72698_e", "playerViewRadius");

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onLoad(final WorldEvent.Load event) {
		if (!(event.world instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.world;
		Ymir.get(world.getWorldInfo().getWorldName()).ifPresent(ymirWorld -> {
			this.load(world, ymirWorld.getConfig());
			this.zone(world, ymirWorld);
		});
	}

	@SubscribeEvent
	public void onEnteringChunk(final EnteringChunk event) {
		if (!(event.entity instanceof EntityPlayerMP) || !(event.entity.worldObj instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.entity.worldObj;
		if (world.theChunkProviderServer.currentChunkProvider instanceof YmirSourceChunkProvider) {
			((YmirSourceChunkProvider) world.theChunkProviderServer.currentChunkProvider).prefetch(event.newChunkX, event.newChunkZ, this.getViewRadius(world) + 1);
			((YmirSourceChunkProvider) world.theChunkProviderServer.currentChunkProvider).spawnCreatures(event.newChunkX, event.newChunkZ);
		}
	}

	@SubscribeEvent
	public void onChunkLoad(final ChunkEvent.Load event) {
		if (!(event.world instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.world;
		if (world.theChunkProviderServer.currentChunkProvider instanceof YmirSourceChunkProvider) {
			((YmirSourceChunkProvider) world.theChunkProviderServer.currentChunkProvider).spawnCreatures(event.getChunk());
		}
	}

	private void load(final WorldServer world, final YmirWorldConfig config) {
		if (this.keepSpawnInMemory != null) {
			try {
				this.keepSpawnInMemory.setBoolean(world, config.isKeepSpawnInMemory());
			} catch (final IllegalAccessException e) {
				System.err.println("[Ymir] Unable to set keepSpawnInMemory on the world " + config.getName() + ": " + e.getMessage());
			}
		}

		if (this.unloadSpawnChunks != null) {
			try {
				this.unloadSpawnChunks.setBoolean(world, !config.isKeepSpawnInMemory() && !config.isKeepLoaded());
			} catch (final IllegalAccessException e) {
				System.err.println("[Ymir] Unable to set unloadSpawnChunks on the world " + config.getName() + ": " + e.getMessage());
			}
		}

		if (config.getSource() != null) {
			world.theChunkProviderServer.currentChunkProvider = new YmirSourceChunkProvider(world, config.getSource());
		}

		if (world.theChunkProviderServer.currentChunkLoader instanceof AnvilChunkLoader) {
			final File directory = ((AnvilChunkLoader) world.theChunkProviderServer.currentChunkLoader).chunkSaveLocation;
			final File template = this.getTemplate(config, directory);
			final int[] blocks = template == null ? null : YmirBlockIdRegistry.inst().getBlocks(config.getTemplate()).orElse(null);
			world.theChunkProviderServer.currentChunkLoader = new YmirChunkLoader(directory, template, blocks, config.isTemporary(), config.isTemporary() && config.isVolatileChunks());
		}
	}

	private void zone(final WorldServer world, final YmirWorld ymirWorld) {
		if (ymirWorld.getBounds().isEmpty()) {
			return;
		}

		final ChunkCoordinates spawn = world.getSpawnPoint();
		if (!ymirWorld.contains(spawn.posX >> 4, spawn.posZ >> 4)) {
			ymirWorld.addBounds(YmirWorldBox.ofBlocks(spawn.posX, spawn.posZ, spawn.posX, spawn.posZ).expand(1));
		}
		ymirWorld.getBounds().forEach(box -> YmirWorldZones.add(world, box));
		if (ymirWorld.getConfig().getTemplate() != null) {
			world.theChunkProviderServer.currentChunkProvider = new YmirTemplateChunkProvider(world, world.theChunkProviderServer.currentChunkProvider, ymirWorld.getBounds());
		}
	}

	private File getTemplate(final YmirWorldConfig config, final File directory) {
		if (!config.isTemporary() || config.getTemplate() == null) {
			return null;
		}

		final Path root = YmirWorldFiles.getDirectory(config.getName()).toPath().toAbsolutePath().normalize();
		return config.getTemplate().toPath().resolve(root.relativize(directory.toPath().toAbsolutePath().normalize())).toFile();
	}

	private int getViewRadius(final WorldServer world) {
		try {
			return this.viewRadius.getInt(world.getPlayerManager());
		} catch (final IllegalAccessException e) {
			return MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		}
	}

	private Field findWorldField(final String name) {
		try {
			return World.class.getField(name);
		} catch (final NoSuchFieldException e) {
			return null;
		}
	}

}