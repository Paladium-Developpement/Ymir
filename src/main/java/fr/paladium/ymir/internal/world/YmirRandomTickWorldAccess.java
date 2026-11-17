package fr.paladium.ymir.internal.world;

import lombok.RequiredArgsConstructor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;

@RequiredArgsConstructor
public class YmirRandomTickWorldAccess implements IWorldAccess {

	private final World world;

	@Override
	public void markBlockForUpdate(final int x, final int y, final int z) {
		YmirWorldRandomTicks.inst().disable(this.world, x, y, z);
	}

	@Override
	public void markBlockForRenderUpdate(final int x, final int y, final int z) {}

	@Override
	public void markBlockRangeForRenderUpdate(final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {}

	@Override
	public void playSound(final String sound, final double x, final double y, final double z, final float volume, final float pitch) {}

	@Override
	public void playSoundToNearExcept(final EntityPlayer player, final String sound, final double x, final double y, final double z, final float volume, final float pitch) {}

	@Override
	public void spawnParticle(final String particle, final double x, final double y, final double z, final double motionX, final double motionY, final double motionZ) {}

	@Override
	public void onEntityCreate(final Entity entity) {}

	@Override
	public void onEntityDestroy(final Entity entity) {}

	@Override
	public void playRecord(final String record, final int x, final int y, final int z) {}

	@Override
	public void broadcastSound(final int sound, final int x, final int y, final int z, final int data) {}

	@Override
	public void playAuxSFX(final EntityPlayer player, final int sound, final int x, final int y, final int z, final int data) {}

	@Override
	public void destroyBlockPartially(final int breaker, final int x, final int y, final int z, final int progress) {}

	@Override
	public void onStaticEntitiesChanged() {}

}