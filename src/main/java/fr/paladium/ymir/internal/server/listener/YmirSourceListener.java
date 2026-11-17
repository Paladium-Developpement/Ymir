package fr.paladium.ymir.internal.server.listener;

import java.lang.reflect.Field;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import fr.paladium.ymir.internal.world.source.YmirSourceChunkLoader;
import fr.paladium.ymir.internal.world.source.YmirSourceChunkProvider;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.event.entity.EntityEvent.EnteringChunk;
import net.minecraftforge.event.world.WorldEvent;

public class YmirSourceListener {

	private final Field keepSpawnInMemory = this.findKeepSpawnInMemory();
	private final Field viewRadius = ReflectionHelper.findField(PlayerManager.class, "field_72698_e", "playerViewRadius");

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onLoad(final WorldEvent.Load event) {
		if (!(event.world instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.world;
		Ymir.get(world.getWorldInfo().getWorldName()).map(YmirWorld::getConfig).ifPresent(config -> this.load(world, config));
	}

	@SubscribeEvent
	public void onEnteringChunk(final EnteringChunk event) {
		if (!(event.entity instanceof EntityPlayerMP) || !(event.entity.worldObj instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.entity.worldObj;
		if (world.theChunkProviderServer.currentChunkProvider instanceof YmirSourceChunkProvider) {
			((YmirSourceChunkProvider) world.theChunkProviderServer.currentChunkProvider).prefetch(event.newChunkX, event.newChunkZ, this.getViewRadius(world) + 2);
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

		if (config.getSource() != null) {
			world.theChunkProviderServer.currentChunkProvider = new YmirSourceChunkProvider(world, config.getSource());
			if (world.theChunkProviderServer.currentChunkLoader instanceof AnvilChunkLoader) {
				world.theChunkProviderServer.currentChunkLoader = new YmirSourceChunkLoader(((AnvilChunkLoader) world.theChunkProviderServer.currentChunkLoader).chunkSaveLocation);
			}
		}
	}

	private int getViewRadius(final WorldServer world) {
		try {
			return this.viewRadius.getInt(world.getPlayerManager());
		} catch (final IllegalAccessException e) {
			return MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		}
	}

	private Field findKeepSpawnInMemory() {
		try {
			return World.class.getField("keepSpawnInMemory");
		} catch (final NoSuchFieldException e) {
			return null;
		}
	}

}