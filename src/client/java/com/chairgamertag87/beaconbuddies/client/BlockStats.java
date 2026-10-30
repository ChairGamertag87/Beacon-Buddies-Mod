package com.chairgamertag87.beaconbuddies.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class BlockStats {
	private static final int REQUEST_INTERVAL_TICKS = 600;

	private static long placed = -1;
	private static int requestCounter;

	private BlockStats() {
	}

	public static void reset() {
		placed = -1;
		requestSoon();
	}

	public static void requestSoon() {
		requestCounter = REQUEST_INTERVAL_TICKS;
	}

	public static void tick(ClientPacketListener connection) {
		if (connection == null || ++requestCounter < REQUEST_INTERVAL_TICKS) {
			return;
		}
		requestCounter = 0;
		connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
	}

	public static void onStatsReceived() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		StatsCounter stats = client.player.getStats();
		long total = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BlockItem) {
				total += stats.getValue(Stats.ITEM_USED, item);
			}
		}
		placed = total;
	}

	public static long placed() {
		return placed;
	}
}
