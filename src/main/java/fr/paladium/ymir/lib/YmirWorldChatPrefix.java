package fr.paladium.ymir.lib;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldChatPrefix {

	private final String url;
	private final String emote;

	public static @NonNull YmirWorldChatPrefix none() {
		return new YmirWorldChatPrefix(null, null);
	}

	public static @NonNull YmirWorldChatPrefix url(final @NonNull String url) {
		return new YmirWorldChatPrefix(url, null);
	}

	public static @NonNull YmirWorldChatPrefix emote(final @NonNull String emote) {
		return new YmirWorldChatPrefix(null, emote);
	}

	public boolean isNone() {
		return this.url == null && this.emote == null;
	}

}