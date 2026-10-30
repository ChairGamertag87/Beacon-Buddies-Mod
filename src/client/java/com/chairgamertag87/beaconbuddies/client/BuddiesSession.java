package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.client.relay.Buddy;
import com.chairgamertag87.beaconbuddies.client.relay.PlayerStat;
import com.chairgamertag87.beaconbuddies.client.relay.RelayClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BuddiesSession {
	public static final String RELAY_URL = "wss://beaconbuddies.habibiserver.dev/beaconbuddies";
	private static final int POS_INTERVAL_TICKS = 10;
	private static final int PLAYERS_CHECK_TICKS = 20;
	private static final int PLAYERS_RESEND_TICKS = 600;

	private static final RelayClient RELAY = new RelayClient(RELAY_URL, new RelayListener());

	private static boolean inGame;
	private static boolean wanted;
	private static boolean enabled;
	private static Map<UUID, Buddy> buddies = Map.of();
	private static int tickCounter;
	private static String lastDimension;
	private static Map<UUID, PlayerStat> stats = Map.of();
	private static long clockOffsetMs;
	private static Map<UUID, String> lastPlayers;
	private static int playersCheckCounter;
	private static int playersResendCounter;
	private static long lastSentPlaced = -1;

	private BuddiesSession() {
	}

	public static void onJoin(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server == null || server.isLan() || client.isLocalServer() || client.player == null) {
			return;
		}
		inGame = true;
		wanted = false;
		enabled = false;
		buddies = Map.of();
		stats = Map.of();
		lastDimension = null;
		BlockStats.reset();
		RELAY.connect(client.player.getUUID(), client.getUser().getName(), normalizeAddress(server.ip));
	}

	public static void onDisconnect() {
		inGame = false;
		wanted = false;
		enabled = false;
		buddies = Map.of();
		stats = Map.of();
		TeamChat.reset();
		RELAY.disconnect();
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (!enabled || player == null || client.level == null) {
			return;
		}
		String dimension = client.level.dimension().identifier().toString();
		boolean dimensionChanged = !dimension.equals(lastDimension);
		if (dimensionChanged || ++tickCounter >= POS_INTERVAL_TICKS) {
			tickCounter = 0;
			lastDimension = dimension;
			RELAY.sendPos(player.getX(), player.getY(), player.getZ(), dimension);
		}
		reportSelf(player, client.getUser().getName());
		BlockStats.tick(client.getConnection());
		long placed = BlockStats.placed();
		if (placed >= 0 && placed != lastSentPlaced) {
			lastSentPlaced = placed;
			RELAY.sendBlocks(placed);
		}
	}

	private static void reportSelf(LocalPlayer player, String name) {
		if (++playersCheckCounter < PLAYERS_CHECK_TICKS) {
			return;
		}
		playersResendCounter += playersCheckCounter;
		playersCheckCounter = 0;
		Map<UUID, String> players = Map.of(player.getUUID(), name);
		if (players.equals(lastPlayers) && playersResendCounter < PLAYERS_RESEND_TICKS) {
			return;
		}
		lastPlayers = players;
		playersResendCounter = 0;
		RELAY.sendPlayers(players);
	}

	public static void request(boolean on) {
		Minecraft client = Minecraft.getInstance();
		if (!inGame) {
			message(client, Component.translatable("beaconbuddies.message.unavailable"));
			return;
		}
		wanted = on;
		if (!RELAY.isOpen()) {
			message(client, Component.translatable("beaconbuddies.message.offline"));
			return;
		}
		RELAY.sendState(on);
	}

	public static void toggle() {
		request(!wanted);
	}

	public static boolean isInGame() {
		return inGame;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean isRelayOpen() {
		return RELAY.isOpen();
	}

	public static boolean isChatAvailable() {
		return enabled && RELAY.supportsChat();
	}

	public static void sendChat(String text) {
		if (isChatAvailable()) {
			RELAY.sendChat(text);
		}
	}

	public static Map<UUID, Buddy> getBuddies() {
		return buddies;
	}

	public static Map<UUID, PlayerStat> getStats() {
		return stats;
	}

	public static long relayNowMs() {
		return System.currentTimeMillis() + clockOffsetMs;
	}

	static String normalizeAddress(String ip) {
		ServerAddress address = ServerAddress.parseString(ip.trim());
		String host = address.getHost().toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return host + ":" + address.getPort();
	}

	private static void message(Minecraft client, Component text) {
		if (client.player != null) {
			client.player.displayClientMessage(text, true);
		}
	}

	private static final class RelayListener implements RelayClient.Listener {
		@Override
		public void onConnected() {
			Minecraft.getInstance().execute(() -> {
				if (wanted) {
					RELAY.sendState(true);
				}
			});
		}

		@Override
		public void onDisconnected() {
			Minecraft.getInstance().execute(() -> {
				enabled = false;
				buddies = Map.of();
				stats = Map.of();
			});
		}

		@Override
		public void onState(boolean on) {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				if (!inGame || on == enabled) {
					return;
				}
				enabled = on;
				lastDimension = null;
				lastPlayers = null;
				lastSentPlaced = -1;
				BlockStats.requestSoon();
				if (!on) {
					buddies = Map.of();
					stats = Map.of();
					TeamChat.leaveTeamTab();
				}
				message(client, Component.translatable(on ? "beaconbuddies.message.enabled" : "beaconbuddies.message.disabled"));
			});
		}

		@Override
		public void onSync(List<Buddy> list) {
			Map<UUID, Buddy> next = list.stream().collect(Collectors.toUnmodifiableMap(Buddy::uuid, Function.identity(), (a, b) -> b));
			Minecraft.getInstance().execute(() -> {
				if (enabled) {
					buddies = next;
				}
			});
		}

		@Override
		public void onChat(UUID uuid, String name, String text) {
			Minecraft.getInstance().execute(() -> {
				if (enabled) {
					TeamChat.receive(name, text);
				}
			});
		}

		@Override
		public void onStats(List<PlayerStat> list, long relayNow) {
			long offset = relayNow - System.currentTimeMillis();
			Map<UUID, PlayerStat> next = list.stream().collect(Collectors.toUnmodifiableMap(PlayerStat::uuid, Function.identity(), (a, b) -> b));
			Minecraft.getInstance().execute(() -> {
				if (enabled) {
					clockOffsetMs = offset;
					stats = next;
				}
			});
		}
	}
}
