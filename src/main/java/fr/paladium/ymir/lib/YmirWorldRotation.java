package fr.paladium.ymir.lib;

import java.util.Random;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum YmirWorldRotation {

	NONE(0),
	CLOCKWISE_90(1),
	CLOCKWISE_180(2),
	CLOCKWISE_270(3);

	private final int quarters;

	private YmirWorldRotation(final int quarters) {
		this.quarters = quarters;
	}

	public static @NonNull YmirWorldRotation of(final int quarters) {
		return YmirWorldRotation.values()[Math.floorMod(quarters, 4)];
	}

	public static @NonNull YmirWorldRotation random(final @NonNull Random random) {
		return YmirWorldRotation.values()[random.nextInt(4)];
	}

	public @NonNull YmirWorldRotation inverse() {
		return YmirWorldRotation.of(-this.quarters);
	}

	public int rotateX(final int x, final int z) {
		return this.quarters == 0 ? x : this.quarters == 1 ? -z - 1 : this.quarters == 2 ? -x - 1 : z;
	}

	public int rotateZ(final int x, final int z) {
		return this.quarters == 0 ? z : this.quarters == 1 ? x : this.quarters == 2 ? -z - 1 : -x - 1;
	}

	public double rotateX(final double x, final double z) {
		return this.quarters == 0 ? x : this.quarters == 1 ? -z : this.quarters == 2 ? -x : z;
	}

	public double rotateZ(final double x, final double z) {
		return this.quarters == 0 ? z : this.quarters == 1 ? x : this.quarters == 2 ? -z : -x;
	}

	public float rotateYaw(final float yaw) {
		return yaw + this.quarters * 90F;
	}

}