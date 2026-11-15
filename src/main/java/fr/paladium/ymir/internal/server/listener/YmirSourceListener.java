package fr.paladium.ymir.internal.server.listener;

import java.lang.reflect.Field;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fr.paladium.ymir.internal.world.source.YmirSourceChunkProvider;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.YmirWorldConfig;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

public class YmirSourceListener {

	private final Field keepSpawnInMemory = this.findKeepSpawnInMemory();

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onLoad(final WorldEvent.Load event) {
		if (!(event.world instanceof WorldServer)) {
			return;
		}

		final WorldServer world = (WorldServer) event.world;
		Ymir.get(world.getWorldInfo().getWorldName()).map(YmirWorld::getConfig).ifPresent(config -> this.load(world, config));
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