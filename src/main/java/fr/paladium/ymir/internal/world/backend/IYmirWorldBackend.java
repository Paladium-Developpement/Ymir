package fr.paladium.ymir.internal.world.backend;

import org.bukkit.World;

import fr.paladium.ymir.lib.YmirWorld;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.WorldServer;

public interface IYmirWorldBackend {

	public WorldServer load(final YmirWorld world);
	public boolean unload(final YmirWorld world, final boolean save);

	public void save(final YmirWorld world);
	public void connect(final NetworkManager manager, final EntityPlayerMP player);
	public World getBukkitWorld(final YmirWorld world);
	public void setSpawn(final YmirWorld world, final int x, final int y, final int z);
	public boolean teleport(final YmirWorld world, final EntityPlayerMP player, final double x, final double y, final double z, final float yaw, final float pitch);

}