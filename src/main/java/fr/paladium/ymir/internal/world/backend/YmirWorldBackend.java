package fr.paladium.ymir.internal.world.backend;

import org.bukkit.Bukkit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YmirWorldBackend {

	public static IYmirWorldBackend get() {
		return Bukkit.getServer() == null ? ForgeYmirWorldBackend.inst() : BukkitYmirWorldBackend.inst();
	}

}