package com.chairgamertag87.beaconbuddies.client.relay;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RelayClient {
	private static final int PROTOCOL_VERSION = 1;
	private static final long MIN_RETRY_SECONDS = 5;
	private static final long MAX_RETRY_SECONDS = 60;

	public interface Listener {
		void onConnected();

		void onDisconnected();

		void onState(boolean enabled);

		void onSync(List<Buddy> buddies);
	}

	private final URI uri;
	private final Listener listener;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "BeaconBuddies-Relay");
		thread.setDaemon(true);
		return thread;
	});

	private String helloJson;
	private WebSocket socket;
	private boolean open;
	private boolean wanted;
	private long retrySeconds = MIN_RETRY_SECONDS;
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

	public RelayClient(String url, Listener listener) {
		this.uri = URI.create(url);
		this.listener = listener;
	}

	public synchronized void connect(UUID uuid, String name, String server) {
		JsonObject hello = new JsonObject();
		hello.addProperty("type", "hello");
		hello.addProperty("v", PROTOCOL_VERSION);
		hello.addProperty("uuid", uuid.toString());
		hello.addProperty("name", name);
		hello.addProperty("server", server);
		this.helloJson = hello.toString();
		this.wanted = true;
		this.retrySeconds = MIN_RETRY_SECONDS;
		openSocket();
	}

	public synchronized void disconnect() {
		wanted = false;
		WebSocket current = socket;
		socket = null;
		boolean wasOpen = open;
		open = false;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
		}
		if (wasOpen) {
			listener.onDisconnected();
		}
	}

	public synchronized boolean isOpen() {
		return open;
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
		msg.addProperty("x", x);
		msg.addProperty("y", y);
		msg.addProperty("z", z);
		msg.addProperty("dim", dimension);
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
		http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(uri, new SocketListener())
				.whenComplete((ws, error) -> {
					if (error != null) {
						BeaconBuddies.LOGGER.warn("Relais injoignable : {}", error.getMessage());
						scheduleRetry();
						return;
					}
					synchronized (this) {
						if (!wanted || !hello.equals(helloJson)) {
							ws.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
							return;
						}
						socket = ws;
						open = true;
						retrySeconds = MIN_RETRY_SECONDS;
						sendChain = CompletableFuture.completedFuture(null);
					}
					send(hello);
					listener.onConnected();
				});
	}

	private synchronized void scheduleRetry() {
		if (!wanted) {
			return;
		}
		long delay = retrySeconds;
		retrySeconds = Math.min(retrySeconds * 2, MAX_RETRY_SECONDS);
		scheduler.schedule(this::openSocket, delay, TimeUnit.SECONDS);
	}

	private void onSocketClosed(WebSocket ws) {
		synchronized (this) {
			if (ws != socket) {
				return;
			}
			socket = null;
			open = false;
		}
		listener.onDisconnected();
		scheduleRetry();
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
			case "state" -> listener.onState(msg.get("enabled").getAsBoolean());
			case "sync" -> listener.onSync(parseBuddies(msg.getAsJsonArray("players")));
			case "error" -> BeaconBuddies.LOGGER.warn("Erreur du relais : {}", msg.get("message"));
			default -> {
			}
		}
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
						p.get("dim").getAsString()));
			} catch (RuntimeException e) {
				BeaconBuddies.LOGGER.warn("Joueur invalide ignoré : {}", element);
			}
		}
		return buddies;
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
			onSocketClosed(ws);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			BeaconBuddies.LOGGER.warn("Connexion au relais perdue : {}", error.getMessage());
			onSocketClosed(ws);
		}
	}
}
