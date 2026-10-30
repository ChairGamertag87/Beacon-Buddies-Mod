package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.client.relay.PlayerStat;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlaytimeList {
	public record Entry(UUID uuid, String name, long sessionSeconds, long totalSeconds) {
	}

	private PlaytimeList() {
	}

	public static List<Entry> sorted() {
		if (!BuddiesSession.isEnabled()) {
			return List.of();
		}
		long now = BuddiesSession.relayNowMs();
		return BuddiesSession.getStats().values().stream()
				.map(stat -> entry(stat, now))
				.sorted(Comparator.comparingLong(Entry::totalSeconds).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public static String format(long seconds) {
		long minutes = Math.max(0, seconds) / 60;
		if (minutes < 60) {
			return minutes + " min";
		}
		long hours = minutes / 60;
		return hours >= 100 ? hours + " h" : String.format("%d h %02d", hours, minutes % 60);
	}

	private static Entry entry(PlayerStat stat, long now) {
		long session = Math.max(0, (now - stat.sessionStart()) / 1000);
		return new Entry(stat.uuid(), stat.name(), session, stat.totalSeconds() + session);
	}
}
