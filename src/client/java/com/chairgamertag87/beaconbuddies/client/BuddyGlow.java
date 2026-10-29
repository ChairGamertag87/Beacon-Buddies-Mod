package com.chairgamertag87.beaconbuddies.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class BuddyGlow {
	private BuddyGlow() {
	}

	public static boolean shouldGlow(Entity entity) {
		return BuddiesSession.isEnabled()
				&& entity instanceof Player
				&& !entity.isInvisible()
				&& BuddiesSession.getBuddies().containsKey(entity.getUUID());
	}

	public static int color() {
		Integer color = BuddiesConfig.get().glowFormatting().getColor();
		return color != null ? color : 0xFFFFFF;
	}
}
