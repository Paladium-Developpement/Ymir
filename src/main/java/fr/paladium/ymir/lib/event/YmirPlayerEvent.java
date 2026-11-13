package fr.paladium.ymir.lib.event;

import fr.paladium.ymir.lib.YmirWorld;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.entity.player.EntityPlayerMP;

@Getter
public abstract class YmirPlayerEvent extends YmirWorldEvent {

	private final EntityPlayerMP player;

	protected YmirPlayerEvent(final YmirWorld world, final @NonNull EntityPlayerMP player) {
		super(world);
		this.player = player;
	}

	public static class Enter extends YmirPlayerEvent {

		public Enter(final YmirWorld world, final EntityPlayerMP player) {
			super(world, player);
		}

	}

	public static class Leave extends YmirPlayerEvent {

		public Leave(final YmirWorld world, final EntityPlayerMP player) {
			super(world, player);
		}

	}

}