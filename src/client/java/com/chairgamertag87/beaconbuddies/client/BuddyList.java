package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.client.relay.Buddy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Comparator;
import java.util.List;

public final class BuddyList {
	public record Entry(Buddy buddy, double distance) {
		public boolean otherDimension() {
			return Double.isInfinite(distance);
		}
	}

	private BuddyList() {
	}

	public static List<Entry> sorted(Minecraft client) {
		LocalPlayer player = client.player;
		if (!BuddiesSession.isEnabled() || player == null || client.level == null) {
			return List.of();
		}
		String dimension = client.level.dimension().identifier().toString();
		return BuddiesSession.getBuddies().values().stream()
				.map(buddy -> new Entry(buddy, distance(player, buddy, dimension)))
				.sorted(Comparator.comparingDouble(Entry::distance).thenComparing(e -> e.buddy().name(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private static double distance(LocalPlayer player, Buddy buddy, String dimension) {
		if (!buddy.dimension().equals(dimension)) {
			return Double.POSITIVE_INFINITY;
		}
		double dx = buddy.x() - player.getX();
		double dy = buddy.y() - player.getY();
		double dz = buddy.z() - player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
