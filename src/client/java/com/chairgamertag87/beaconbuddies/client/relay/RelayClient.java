package com.chairgamertag87.beaconbuddies.client.relay;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RelayClient {
	private static final int PROTOCOL_VERSION = 1;
	private static final long MIN_RETRY_SECONDS = 5;
	private static final long MAX_RETRY_SECONDS = 60;
	private static final int CLOSE_REJECTED = 1008;
	private static final int CLOSE_OVERLOADED = 1013;
	private static final String CLIENT_VERSION = FabricLoader.getInstance().getModContainer(BeaconBuddies.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");

	public interface Listener {
		void onConnected();

		void onDisconnected();

		void onState(boolean enabled);

		void onSync(List<Buddy> buddies);

		void onStats(List<PlayerStat> stats, long relayNow);

		void onChat(UUID uuid, String name, String text);

		void onRejected();
	}

	public interface Authenticator {
		void join(String challenge) throws Exception;
	}

	private final URI uri;
	private final Listener listener;
	private final Authenticator authenticator;
	private HttpClient http;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "BeaconBuddies-Relay");
		thread.setDaemon(true);
		return thread;
	});

	private String helloJson;
	private WebSocket socket;
	private boolean open;
	private boolean ready;
	private volatile boolean authFailed;
	private volatile boolean failed;
	private volatile boolean statsSupported;
	private volatile boolean blocksSupported;
	private volatile boolean chatSupported;
	private boolean wanted;
	private long retrySeconds = MIN_RETRY_SECONDS;
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

	public RelayClient(String url, Listener listener, Authenticator authenticator) {
		this.uri = URI.create(url);
		this.listener = listener;
		this.authenticator = authenticator;
	}

	public synchronized void connect(UUID uuid, String name, String server) {
		JsonObject hello = new JsonObject();
		hello.addProperty("type", "hello");
		hello.addProperty("v", PROTOCOL_VERSION);
		hello.addProperty("uuid", uuid.toString());
		hello.addProperty("name", name);
		hello.addProperty("server", server);
		hello.addProperty("client", CLIENT_VERSION);
		this.helloJson = hello.toString();
		this.wanted = true;
		this.authFailed = false;
		this.failed = false;
		this.retrySeconds = MIN_RETRY_SECONDS;
		openSocket();
	}

	public synchronized void disconnect() {
		wanted = false;
		WebSocket current = socket;
		socket = null;
		boolean wasReady = ready;
		open = false;
		ready = false;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
		}
		if (wasReady) {
			listener.onDisconnected();
		}
	}

	public synchronized boolean isOpen() {
		return open && ready;
	}

	public synchronized boolean isWanted() {
		return wanted;
	}

	public boolean hasFailed() {
		return failed;
	}

	public boolean hasAuthFailed() {
		return authFailed;
	}

	public boolean supportsStats() {
		return statsSupported;
	}

	public boolean supportsBlocks() {
		return blocksSupported;
	}

	public boolean supportsChat() {
		return chatSupported;
	}

	public void sendState(boolean enabled) {
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "state");
		msg.addProperty("enabled", enabled);
		send(msg.toString());
	}

	public void sendPos(double x, double y, double z, String dimension) {
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "pos");
		msg.addProperty("x", round(x));
		msg.addProperty("y", round(y));
		msg.addProperty("z", round(z));
		msg.addProperty("dim", dimension);
		send(msg.toString());
	}

	public void sendPlayers(Map<UUID, String> players) {
		if (!statsSupported) {
			return;
		}
		JsonArray list = new JsonArray();
		players.forEach((uuid, name) -> {
			JsonObject player = new JsonObject();
			player.addProperty("uuid", uuid.toString());
			player.addProperty("name", name);
			list.add(player);
		});
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "players");
		msg.add("players", list);
		send(msg.toString());
	}

	public void sendBlocks(long placed) {
		if (!blocksSupported) {
			return;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "blocks");
		msg.addProperty("placed", placed);
		send(msg.toString());
	}

	public void sendChat(String text) {
		if (!chatSupported) {
			return;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "chat");
		msg.addProperty("text", text);
		send(msg.toString());
	}

	private synchronized void send(String json) {
		if (!open || socket == null) {
			return;
		}
		WebSocket target = socket;
		sendChain = sendChain
				.handle((v, e) -> null)
				.thenCompose(v -> target.sendText(json, true));
	}

	private synchronized void openSocket() {
		if (!wanted) {
			return;
		}
		String hello = helloJson;
		CompletableFuture<WebSocket> connecting;
		try {
			if (http == null) {
				http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			}
			connecting = http.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(uri, new SocketListener());
		} catch (RuntimeException e) {
			BeaconBuddies.LOGGER.warn("Impossible d'ouvrir la connexion au relais : {}", e.getMessage());
			scheduleRetry(0);
			return;
		}
		connecting.whenComplete((ws, error) -> {
			if (error != null) {
				BeaconBuddies.LOGGER.warn("Relais injoignable : {}", error.getMessage());
				scheduleRetry(0);
				return;
			}
			synchronized (this) {
				if (!wanted || !hello.equals(helloJson)) {
					ws.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
					return;
				}
				socket = ws;
				open = true;
				ready = false;
				statsSupported = false;
				blocksSupported = false;
				chatSupported = false;
				sendChain = CompletableFuture.completedFuture(null);
			}
			send(hello);
		});
	}

	private synchronized void scheduleRetry(long minSeconds) {
		if (!wanted) {
			return;
		}
		failed = true;
		long delay = Math.max(retrySeconds, minSeconds) * 1000;
		retrySeconds = Math.min(retrySeconds * 2, MAX_RETRY_SECONDS);
		long jitter = ThreadLocalRandom.current().nextLong(delay / 2 + 1);
		scheduler.schedule(this::openSocket, delay + jitter, TimeUnit.MILLISECONDS);
	}

	private void reject() {
		authFailed = true;
		disconnect();
		listener.onRejected();
	}

	private void onSocketClosed(WebSocket ws, int statusCode) {
		boolean wasReady;
		synchronized (this) {
			if (ws != socket) {
				return;
			}
			socket = null;
			open = false;
			wasReady = ready;
			ready = false;
		}
		if (wasReady) {
			listener.onDisconnected();
		}
		if (statusCode == CLOSE_REJECTED) {
			reject();
		} else {
			scheduleRetry(statusCode == CLOSE_OVERLOADED ? MAX_RETRY_SECONDS : 0);
		}
	}

	private void handleMessage(String text) {
		JsonObject msg;
		try {
			msg = JsonParser.parseString(text).getAsJsonObject();
		} catch (RuntimeException e) {
			BeaconBuddies.LOGGER.warn("Message du relais illisible : {}", text);
			return;
		}
		String type = msg.has("type") ? msg.get("type").getAsString() : "";
		switch (type) {
			case "welcome" -> {
				statsSupported = hasFeature(msg, "stats");
				blocksSupported = hasFeature(msg, "blocks");
				chatSupported = hasFeature(msg, "chat");
				if (msg.has("challenge")) {
					authenticate(msg.get("challenge").getAsString());
				} else {
					markReady();
				}
			}
			case "authenticated" -> markReady();
			case "state" -> listener.onState(msg.get("enabled").getAsBoolean());
			case "sync" -> listener.onSync(parseBuddies(msg.getAsJsonArray("players")));
			case "stats" -> listener.onStats(parseStats(msg.getAsJsonArray("players")), msg.get("now").getAsLong());
			case "chat" -> parseChat(msg);
			case "error" -> BeaconBuddies.LOGGER.warn("Erreur du relais : {}", msg.get("message"));
			default -> {
			}
		}
	}

	private void authenticate(String challenge) {
		WebSocket target;
		synchronized (this) {
			target = socket;
		}
		scheduler.execute(() -> {
			try {
				authenticator.join(challenge);
			} catch (Exception e) {
				BeaconBuddies.LOGGER.warn("Vérification du compte Minecraft impossible : {}", e.getMessage());
				reject();
				return;
			}
			authFailed = false;
			synchronized (this) {
				if (target != socket) {
					return;
				}
			}
			JsonObject msg = new JsonObject();
			msg.addProperty("type", "auth");
			send(msg.toString());
		});
	}

	private void markReady() {
		synchronized (this) {
			if (!open || ready) {
				return;
			}
			ready = true;
			failed = false;
			retrySeconds = MIN_RETRY_SECONDS;
		}
		listener.onConnected();
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

	private static List<Buddy> parseBuddies(JsonArray players) {
		List<Buddy> buddies = new ArrayList<>();
		if (players == null) {
			return buddies;
		}
		for (JsonElement element : players) {
			try {
				JsonObject p = element.getAsJsonObject();
				buddies.add(new Buddy(
						UUID.fromString(p.get("uuid").getAsString()),
						p.get("name").getAsString(),
						p.get("x").getAsDouble(),
						p.get("y").getAsDouble(),
						p.get("z").getAsDouble(),
						p.get("dim").getAsString(),
						p.has("placed") ? p.get("placed").getAsLong() : -1));
			} catch (RuntimeException e) {
				BeaconBuddies.LOGGER.warn("Joueur invalide ignoré : {}", element);
			}
		}
		return buddies;
	}

	private void parseChat(JsonObject msg) {
		try {
			listener.onChat(UUID.fromString(msg.get("uuid").getAsString()), msg.get("name").getAsString(), msg.get("text").getAsString());
		} catch (RuntimeException e) {
			BeaconBuddies.LOGGER.warn("Message de chat invalide ignoré");
		}
	}

	private static boolean hasFeature(JsonObject welcome, String feature) {
		JsonArray features = welcome.getAsJsonArray("features");
		if (features == null) {
			return false;
		}
		for (JsonElement element : features) {
			if (feature.equals(element.getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static List<PlayerStat> parseStats(JsonArray players) {
		List<PlayerStat> stats = new ArrayList<>();
		if (players == null) {
			return stats;
		}
		for (JsonElement element : players) {
			try {
				JsonObject p = element.getAsJsonObject();
				stats.add(new PlayerStat(
						UUID.fromString(p.get("uuid").getAsString()),
						p.get("name").getAsString(),
						p.get("totalSeconds").getAsLong(),
						p.get("sessionStart").getAsLong()));
			} catch (RuntimeException e) {
				BeaconBuddies.LOGGER.warn("Stat invalide ignorée : {}", element);
			}
		}
		return stats;
	}

	private class SocketListener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				String text = buffer.toString();
				buffer.setLength(0);
				handleMessage(text);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			onSocketClosed(ws, statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			BeaconBuddies.LOGGER.warn("Connexion au relais perdue : {}", error.getMessage());
			onSocketClosed(ws, -1);
		}
	}
}
