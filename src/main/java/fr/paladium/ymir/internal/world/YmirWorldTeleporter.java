package fr.paladium.ymir.internal.world;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public class YmirWorldTeleporter extends Teleporter {

	private final double x;
	private final double y;
	private final double z;
	private final float  yaw;
	private final float  pitch;

	public YmirWorldTeleporter(final WorldServer world, final double x, final double y, final double z, final float yaw, final float pitch) {
		super(world);
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	@Override
	public void placeInPortal(final Entity entity, final double x, final double y, final double z, final float yaw) {
		entity.setLocationAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
		entity.motionX = 0D;
		entity.motionY = 0D;
		entity.motionZ = 0D;
	}

}