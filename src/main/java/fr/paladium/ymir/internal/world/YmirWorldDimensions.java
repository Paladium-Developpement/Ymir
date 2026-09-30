package fr.paladium.ymir.internal.world;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldDimensions {

	public static WorldServer find(final String name) {
		for (final WorldServer world : MinecraftServer.getServer().worldServers) {
			if (name.equals(world.getWorldInfo().getWorldName())) {
				return world;
			}
		}
		return null;
	}

	public static void unregister(final int dimensionId) {
		if (DimensionManager.getWorld(dimensionId) == null && DimensionManager.isDimensionRegistered(dimensionId)) {
			DimensionManager.unregisterDimension(dimensionId);
		}
	}

}