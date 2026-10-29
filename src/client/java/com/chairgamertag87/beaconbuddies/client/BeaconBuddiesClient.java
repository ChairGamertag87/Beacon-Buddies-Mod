package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

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

		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, BeaconBuddies.id("buddies"), new BuddiesHud());
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
		if (!BuddiesSession.isEnabled()) {
			return status(ctx);
		}
		List<BuddyList.Entry> entries = BuddyList.sorted(Minecraft.getInstance());
		if (entries.isEmpty()) {
			ctx.getSource().sendFeedback(Component.translatable("beaconbuddies.list.empty"));
			return 1;
		}
		for (BuddyList.Entry entry : entries) {
			String name = entry.buddy().name();
			Component line = entry.otherDimension()
					? Component.translatable("beaconbuddies.list.other_dimension", name)
					: Component.translatable("beaconbuddies.list.entry", name, Math.round(entry.distance()));
			ctx.getSource().sendFeedback(line);
		}
		return 1;
	}
}
