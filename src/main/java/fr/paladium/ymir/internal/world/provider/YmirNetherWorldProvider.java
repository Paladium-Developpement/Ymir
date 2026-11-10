package fr.paladium.ymir.internal.world.provider;

import net.minecraft.world.WorldProviderHell;

public class YmirNetherWorldProvider extends WorldProviderHell {

	@Override
	public void registerWorldChunkManager() {
		final int dimensionId = this.dimensionId;
		super.registerWorldChunkManager();
		this.dimensionId = dimensionId;
	}

	@Override
	public String getSaveFolder() {
		return "DIM-1";
	}

}