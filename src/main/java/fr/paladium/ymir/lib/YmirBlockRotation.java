package fr.paladium.ymir.lib;

import lombok.NonNull;

@FunctionalInterface
public interface YmirBlockRotation {

	public int rotate(final int metadata, final @NonNull YmirWorldRotation rotation);

}