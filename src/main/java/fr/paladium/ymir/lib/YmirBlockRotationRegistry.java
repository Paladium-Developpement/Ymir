package fr.paladium.ymir.lib;

import java.util.IdentityHashMap;
import java.util.Map;

import lombok.NonNull;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public final class YmirBlockRotationRegistry {

	private static final YmirBlockRotationRegistry INSTANCE = new YmirBlockRotationRegistry();

	private final Map<Block, YmirBlockRotation> rotations = new IdentityHashMap<>();

	private volatile byte[][] table;

	private YmirBlockRotationRegistry() {
		final YmirBlockRotation axis = this.quarter((metadata, quarter) -> (metadata & 12) == 4 ? metadata & 3 | 8 : (metadata & 12) == 8 ? metadata & 3 | 4 : metadata);
		final YmirBlockRotation horizontal = this.quarter((metadata, quarter) -> metadata & ~3 | metadata + 1 & 3);
		final YmirBlockRotation facing = this.quarter((metadata, quarter) -> metadata & ~7 | this.facing(metadata & 7));
		final YmirBlockRotation wall = this.quarter((metadata, quarter) -> metadata & ~7 | this.wall(metadata & 7));
		final YmirBlockRotation stairs = this.quarter((metadata, quarter) -> metadata & ~3 | new int[] { 2, 3, 1, 0 }[metadata & 3]);
		final YmirBlockRotation trapdoor = this.quarter((metadata, quarter) -> metadata & ~3 | new int[] { 3, 2, 0, 1 }[metadata & 3]);

		this.register(axis, Blocks.log, Blocks.log2, Blocks.hay_block);
		this.register(horizontal, Blocks.pumpkin, Blocks.lit_pumpkin, Blocks.bed, Blocks.fence_gate, Blocks.cocoa, Blocks.end_portal_frame, Blocks.unpowered_repeater, Blocks.powered_repeater, Blocks.unpowered_comparator, Blocks.powered_comparator, Blocks.tripwire_hook, Blocks.anvil);
		this.register(facing, Blocks.ladder, Blocks.wall_sign, Blocks.furnace, Blocks.lit_furnace, Blocks.chest, Blocks.trapped_chest, Blocks.ender_chest, Blocks.dispenser, Blocks.dropper, Blocks.hopper, Blocks.piston, Blocks.sticky_piston, Blocks.piston_head, Blocks.skull);
		this.register(wall, Blocks.torch, Blocks.redstone_torch, Blocks.unlit_redstone_torch, Blocks.stone_button, Blocks.wooden_button);
		this.register(stairs, Blocks.oak_stairs, Blocks.stone_stairs, Blocks.brick_stairs, Blocks.stone_brick_stairs, Blocks.nether_brick_stairs, Blocks.sandstone_stairs, Blocks.spruce_stairs, Blocks.birch_stairs, Blocks.jungle_stairs, Blocks.quartz_stairs, Blocks.acacia_stairs, Blocks.dark_oak_stairs);
		this.register(trapdoor, Blocks.trapdoor);
		this.register(this.quarter((metadata, quarter) -> (metadata & 8) != 0 ? metadata : metadata & ~3 | metadata + 1 & 3), Blocks.wooden_door, Blocks.iron_door);
		this.register(this.quarter((metadata, quarter) -> (metadata << 1 | metadata >> 3) & 15), Blocks.vine);
		this.register(this.quarter((metadata, quarter) -> metadata == 3 ? 4 : metadata == 4 ? 3 : metadata), Blocks.quartz_block);
		this.register((metadata, rotation) -> metadata + rotation.getQuarters() * 4 & 15, Blocks.standing_sign);
		this.register(this.quarter((metadata, quarter) -> metadata >= 1 && metadata <= 9 ? new int[] { 0, 3, 6, 9, 2, 5, 8, 1, 4, 7 }[metadata] : metadata), Blocks.brown_mushroom_block, Blocks.red_mushroom_block);
		this.register(this.quarter((metadata, quarter) -> {
			final int position = metadata & 7;
			final int rotated = position >= 1 && position <= 4 ? this.wall(position) : position == 5 ? 6 : position == 6 ? 5 : position == 0 ? 7 : 0;
			return metadata & 8 | rotated;
		}), Blocks.lever);
	}

	public static @NonNull YmirBlockRotationRegistry inst() {
		return YmirBlockRotationRegistry.INSTANCE;
	}

	public @NonNull YmirBlockRotationRegistry register(final @NonNull YmirBlockRotation rotation, final @NonNull Block... blocks) {
		for (final Block block : blocks) {
			this.rotations.put(block, rotation);
		}
		this.table = null;
		return this;
	}

	public int rotate(final int id, final int metadata, final @NonNull YmirWorldRotation rotation) {
		return rotation == YmirWorldRotation.NONE ? metadata : this.table()[rotation.getQuarters()][id << 4 | metadata & 15];
	}

	public int rotate(final @NonNull Block block, final int metadata, final @NonNull YmirWorldRotation rotation) {
		final YmirBlockRotation found = this.rotations.get(block);
		return found == null || rotation == YmirWorldRotation.NONE ? metadata : found.rotate(metadata, rotation) & 15;
	}

	private byte[][] table() {
		final byte[][] cached = this.table;
		if (cached != null) {
			return cached;
		}

		final byte[][] rotated = new byte[4][65536];
		for (final YmirWorldRotation rotation : YmirWorldRotation.values()) {
			for (int index = 0; index < 65536; index++) {
				rotated[rotation.getQuarters()][index] = (byte) (index & 15);
			}
		}

		for (final Map.Entry<Block, YmirBlockRotation> entry : this.rotations.entrySet()) {
			final int id = Block.getIdFromBlock(entry.getKey());
			if (id < 0 || id > 4095) {
				continue;
			}

			for (final YmirWorldRotation rotation : YmirWorldRotation.values()) {
				for (int metadata = 0; metadata < 16; metadata++) {
					rotated[rotation.getQuarters()][id << 4 | metadata] = (byte) this.rotate(entry.getKey(), metadata, rotation);
				}
			}
		}

		this.table = rotated;
		return rotated;
	}

	private YmirBlockRotation quarter(final YmirBlockRotation step) {
		return (metadata, rotation) -> {
			int rotated = metadata;
			for (int index = 0; index < rotation.getQuarters(); index++) {
				rotated = step.rotate(rotated, YmirWorldRotation.CLOCKWISE_90);
			}
			return rotated;
		};
	}

	private int facing(final int facing) {
		return facing == 2 ? 5 : facing == 5 ? 3 : facing == 3 ? 4 : facing == 4 ? 2 : facing;
	}

	private int wall(final int position) {
		return position == 1 ? 3 : position == 3 ? 2 : position == 2 ? 4 : position == 4 ? 1 : position;
	}

}