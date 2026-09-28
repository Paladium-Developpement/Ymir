package fr.paladium.ymir.lib;

import lombok.NonNull;

public class YmirWorldException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public YmirWorldException(final @NonNull String message) {
		super(message);
	}

	public YmirWorldException(final @NonNull String message, final @NonNull Throwable cause) {
		super(message, cause);
	}

}