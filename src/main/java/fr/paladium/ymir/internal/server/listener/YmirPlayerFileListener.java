package fr.paladium.ymir.internal.server.listener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class YmirPlayerFileListener {

	@SubscribeEvent
	public void onSave(final PlayerEvent.SaveToFile event) {
		final File file = event.getPlayerFile("ymir");
		final Optional<YmirWorld> world = Ymir.get(event.entityPlayer);
		try {
			if (world.isPresent()) {
				Files.write(file.toPath(), world.get().getName().getBytes(StandardCharsets.UTF_8));
			} else {
				Files.deleteIfExists(file.toPath());
			}
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to save the world of " + event.entityPlayer.getCommandSenderName() + ": " + e.getMessage());
		}
	}

	@SubscribeEvent
	public void onLoad(final PlayerEvent.LoadFromFile event) {
		final WorldServer saved = DimensionManager.getWorld(event.entityPlayer.dimension);
		final Optional<YmirWorld> world = saved == null ? Optional.empty() : Ymir.get(saved);
		if (!world.isPresent() || world.get().getName().equals(this.read(event.getPlayerFile("ymir")))) {
			return;
		}

		final WorldServer overworld = DimensionManager.getWorld(0);
		final ChunkCoordinates spawn = overworld.provider.getRandomizedSpawnPoint();
		event.entityPlayer.dimension = 0;
		event.entityPlayer.setPosition(spawn.posX, spawn.posY, spawn.posZ);
	}

	private String read(final File file) {
		if (!file.isFile()) {
			return null;
		}

		try {
			return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			System.err.println("[Ymir] Unable to read the saved world " + file.getName() + ": " + e.getMessage());
			return null;
		}
	}

}