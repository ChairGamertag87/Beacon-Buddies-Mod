package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
import com.chairgamertag87.beaconbuddies.client.relay.Buddy;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class BeaconBuddiesClient implements ClientModInitializer {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(BeaconBuddies.id("main"));

	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.beaconbuddies.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BuddiesSession.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BuddiesSession.onDisconnect());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				BuddiesSession.toggle();
			}
			BuddiesSession.tick(client);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommand(dispatcher));
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("buddies")
				.executes(BeaconBuddiesClient::status)
				.then(ClientCommandManager.literal("on").executes(ctx -> run(() -> BuddiesSession.request(true))))
				.then(ClientCommandManager.literal("off").executes(ctx -> run(() -> BuddiesSession.request(false))))
				.then(ClientCommandManager.literal("toggle").executes(ctx -> run(BuddiesSession::toggle)))
				.then(ClientCommandManager.literal("list").executes(BeaconBuddiesClient::list)));
	}

	private static int run(Runnable action) {
		action.run();
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		String key;
		if (!BuddiesSession.isInGame()) {
			key = "beaconbuddies.status.unavailable";
		} else if (!BuddiesSession.isRelayOpen()) {
			key = "beaconbuddies.status.offline";
		} else {
			key = BuddiesSession.isEnabled() ? "beaconbuddies.status.enabled" : "beaconbuddies.status.disabled";
		}
		ctx.getSource().sendFeedback(Component.translatable(key));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (!BuddiesSession.isEnabled() || player == null || client.level == null) {
			return status(ctx);
		}
		String dimension = client.level.dimension().identifier().toString();
		List<Buddy> sorted = BuddiesSession.getBuddies().values().stream()
				.sorted(Comparator.comparingDouble(b -> distance(player, b, dimension)))
				.toList();
		if (sorted.isEmpty()) {
			ctx.getSource().sendFeedback(Component.translatable("beaconbuddies.list.empty"));
			return 1;
		}
		for (Buddy buddy : sorted) {
			double distance = distance(player, buddy, dimension);
			Component line = Double.isInfinite(distance)
					? Component.translatable("beaconbuddies.list.other_dimension", buddy.name())
					: Component.translatable("beaconbuddies.list.entry", buddy.name(), Math.round(distance));
			ctx.getSource().sendFeedback(line);
		}
		return 1;
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
