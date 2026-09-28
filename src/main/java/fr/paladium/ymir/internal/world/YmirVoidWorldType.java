package fr.paladium.ymir.internal.world;

import fr.paladium.ymir.lib.YmirWorldType;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;

public class YmirVoidWorldType extends WorldType {

	public static final int SPAWN_HEIGHT = 64;

	private YmirVoidWorldType() {
		super(YmirWorldType.VOID.getName());
	}

	public static void register() {
		if (WorldType.parseWorldType(YmirWorldType.VOID.getName()) == null) {
			new YmirVoidWorldType();
		}
	}

	@Override
	public WorldChunkManager getChunkManager(final World world) {
		return new WorldChunkManagerHell(BiomeGenBase.plains, 0.5F);
	}

	@Override
	public IChunkProvider getChunkGenerator(final World world, final String generatorOptions) {
		return new YmirVoidChunkProvider(world);
	}

}