package com.chairgamertag87.beaconbuddies.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HudTable {
	public static final double NO_DISTANCE = Double.NaN;
	public static final long UNKNOWN = -1;

	public record Row(UUID uuid, String name, boolean self, boolean buddy, double distance, long sessionSeconds, long totalSeconds, long placed) {
		public boolean otherDimension() {
			return Double.isInfinite(distance);
		}

		Row withPlaytime(long session, long total) {
			return new Row(uuid, name, self, buddy, distance, session, total, placed);
		}
	}

	private HudTable() {
	}

	public static List<Row> rows(Minecraft client) {
		if (!BuddiesSession.isEnabled() || client.player == null) {
			return List.of();
		}
		UUID selfId = client.player.getUUID();
		Map<UUID, Row> rows = new LinkedHashMap<>();
		rows.put(selfId, new Row(selfId, client.getUser().getName(), true, false, NO_DISTANCE, UNKNOWN, UNKNOWN, BlockStats.placed()));
		for (BuddyList.Entry entry : BuddyList.sorted(client)) {
			rows.putIfAbsent(entry.buddy().uuid(), new Row(entry.buddy().uuid(), entry.buddy().name(), false, true, entry.distance(), UNKNOWN, UNKNOWN, entry.buddy().placed()));
		}
		for (PlaytimeList.Entry entry : PlaytimeList.sorted()) {
			Row row = rows.get(entry.uuid());
			if (row != null) {
				rows.put(entry.uuid(), row.withPlaytime(entry.sessionSeconds(), entry.totalSeconds()));
			}
		}
		return new ArrayList<>(rows.values());
	}

	public static String formatCount(long count) {
		if (count < 1_000) {
			return String.valueOf(count);
		}
		if (count < 1_000_000) {
			return compact(count, 1_000, "k");
		}
		return compact(count, 1_000_000, "M");
	}

	private static String compact(long count, long unit, String suffix) {
		long scaled = count / unit;
		if (scaled >= 100) {
			return scaled + suffix;
		}
		long tenths = count * 10 / unit % 10;
		return tenths == 0 ? scaled + suffix : String.format(Locale.ROOT, "%d.%d%s", scaled, tenths, suffix);
	}
}
