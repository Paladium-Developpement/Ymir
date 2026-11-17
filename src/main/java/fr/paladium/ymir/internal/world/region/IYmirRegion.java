package fr.paladium.ymir.internal.world.region;

import java.io.IOException;
import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;

public interface IYmirRegion {

	public boolean exists(final int chunkX, final int chunkZ);
	public Optional<NBTTagCompound> read(final int chunkX, final int chunkZ) throws IOException;

	public void close() throws IOException;

}