package fr.paladium.ymir.internal.world.provider;

import net.minecraft.world.WorldProviderEnd;

public class YmirEndWorldProvider extends WorldProviderEnd {

	@Override
	public void registerWorldChunkManager() {
		final int dimensionId = this.dimensionId;
		super.registerWorldChunkManager();
		this.dimensionId = dimensionId;
	}

	@Override
	public String getSaveFolder() {
		return "DIM1";
	}

}