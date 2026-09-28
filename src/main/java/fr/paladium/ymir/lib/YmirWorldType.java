package fr.paladium.ymir.lib;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum YmirWorldType {

	VOID("ymir_void"),
	NORMAL("default"),
	FLAT("flat"),
	AMPLIFIED("amplified"),
	LARGE_BIOMES("largeBiomes");

	private final String name;

	private YmirWorldType(final @NonNull String name) {
		this.name = name;
	}

	public boolean isVoid() {
		return this == YmirWorldType.VOID;
	}

}