package fr.paladium.ymir.lib;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldTab {

	private final YmirWorldTabScope scope;

	private boolean visible = true;

	public static @NonNull YmirWorldTab create(final @NonNull YmirWorldTabScope scope) {
		return new YmirWorldTab(scope);
	}

	public @NonNull YmirWorldTab visible(final boolean visible) {
		this.visible = visible;
		return this;
	}

}