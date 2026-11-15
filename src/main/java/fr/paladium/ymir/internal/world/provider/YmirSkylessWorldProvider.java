package fr.paladium.ymir.internal.world.provider;

import lombok.Getter;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.DimensionManager;

public class YmirSkylessWorldProvider extends WorldProviderSurface {

	@Getter
	private static int type;

	public static void register() {
		int type = 1000;
		while (!DimensionManager.registerProviderType(type, YmirSkylessWorldProvider.class, false)) {
			type++;
		}
		YmirSkylessWorldProvider.type = type;
	}

	@Override
	public void registerWorldChunkManager() {
		super.registerWorldChunkManager();
		this.hasNoSky = true;
	}

	@Override
	protected void generateLightBrightnessTable() {
		for (int level = 0; level <= 15; level++) {
			final float darkness = 1F - level / 15F;
			this.lightBrightnessTable[level] = (1F - darkness) / (darkness * 3F + 1F) * 0.9F + 0.1F;
		}
	}

	@Override
	public String getSaveFolder() {
		return null;
	}

}