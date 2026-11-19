package fr.paladium.ymir.internal.world.region;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;

public interface IYmirRegion extends Closeable {

	public boolean exists(final int chunkX, final int chunkZ);
	public Optional<NBTTagCompound> read(final int chunkX, final int chunkZ) throws IOException;

}