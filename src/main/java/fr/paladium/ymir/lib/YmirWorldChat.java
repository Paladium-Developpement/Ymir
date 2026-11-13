package fr.paladium.ymir.lib;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldChat {

	private String              name;
	private boolean             server = true;
	private boolean             global = true;
	private YmirWorldChatPrefix prefix;

	public static @NonNull YmirWorldChat create() {
		return new YmirWorldChat();
	}

	public @NonNull YmirWorldChat name(final @NonNull String name) {
		this.name = name;
		return this;
	}

	public @NonNull YmirWorldChat server(final boolean server) {
		this.server = server;
		return this;
	}

	public @NonNull YmirWorldChat global(final boolean global) {
		this.global = global;
		return this;
	}

	public @NonNull YmirWorldChat prefix(final @NonNull YmirWorldChatPrefix prefix) {
		this.prefix = prefix;
		return this;
	}

}