package fr.paladium.ymir.internal.world.provider;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldProviderSurface;

public class YmirSurfaceWorldProvider extends WorldProviderSurface {

	@Override
	public String getSaveFolder() {
		return null;
	}

	@Override
	public ChunkCoordinates getRandomizedSpawnPoint() {
		return this.worldObj.getSpawnPoint();
	}

}