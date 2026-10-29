package com.chairgamertag87.beaconbuddies.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class BuddyGlow {
	public static final int DEFAULT_COLOR = 0xFFFFFF;

	private BuddyGlow() {
	}

	public static boolean shouldGlow(Entity entity) {
		return BuddiesSession.isEnabled()
				&& entity instanceof Player
				&& !entity.isInvisible()
				&& BuddiesSession.getBuddies().containsKey(entity.getUUID());
	}

	public static int color() {
		return DEFAULT_COLOR;
	}
}
