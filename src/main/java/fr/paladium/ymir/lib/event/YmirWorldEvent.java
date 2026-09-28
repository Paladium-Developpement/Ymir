package fr.paladium.ymir.lib.event;

import cpw.mods.fml.common.eventhandler.Event;
import fr.paladium.ymir.lib.YmirWorld;
import lombok.Getter;
import lombok.NonNull;

@Getter
public abstract class YmirWorldEvent extends Event {

	private final YmirWorld world;

	protected YmirWorldEvent(final @NonNull YmirWorld world) {
		this.world = world;
	}

	public static class Load extends YmirWorldEvent {

		public Load(final YmirWorld world) {
			super(world);
		}

	}

	public static class Create extends YmirWorldEvent {

		public Create(final YmirWorld world) {
			super(world);
		}

	}

	public static class Delete extends YmirWorldEvent {

		public Delete(final YmirWorld world) {
			super(world);
		}

	}

	public static class Unload extends YmirWorldEvent {

		public Unload(final YmirWorld world) {
			super(world);
		}

	}

}