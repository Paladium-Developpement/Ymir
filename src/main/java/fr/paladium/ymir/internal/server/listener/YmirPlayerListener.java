package fr.paladium.ymir.internal.server.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorld;
import fr.paladium.ymir.lib.event.YmirPlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;

public class YmirPlayerListener {

	private final Map<UUID, YmirWorld> worlds = new HashMap<>();

	@SubscribeEvent
	public void onTick(final PlayerTickEvent event) {
		if (event.phase != Phase.END || !(event.player instanceof EntityPlayerMP)) {
			return;
		}

		final EntityPlayerMP player = (EntityPlayerMP) event.player;
		final YmirWorld current = Ymir.get(player).orElse(null);
		final YmirWorld previous = this.worlds.get(player.getUniqueID());
		if (current == previous) {
			return;
		}

		this.leave(player);
		if (current != null) {
			this.worlds.put(player.getUniqueID(), current);
			MinecraftForge.EVENT_BUS.post(new YmirPlayerEvent.Enter(current, player));
		}
	}

	@SubscribeEvent
	public void onLogout(final PlayerLoggedOutEvent event) {
		if (event.player instanceof EntityPlayerMP) {
			this.leave((EntityPlayerMP) event.player);
		}
	}

	private void leave(final EntityPlayerMP player) {
		final YmirWorld previous = this.worlds.remove(player.getUniqueID());
		if (previous != null) {
			MinecraftForge.EVENT_BUS.post(new YmirPlayerEvent.Leave(previous, player));
		}
	}

}