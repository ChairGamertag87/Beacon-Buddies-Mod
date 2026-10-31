package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
import com.chairgamertag87.beaconbuddies.client.screen.BuddiesSettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BeaconBuddiesClient implements ClientModInitializer {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(BeaconBuddies.id("main"));

	private static KeyMapping toggleKey;
	private static KeyMapping menuKey;
	private static KeyMapping hudKey;

	@Override
	public void onInitializeClient() {
		BuddiesConfig.load();

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconbuddies.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_G, KEY_CATEGORY));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconbuddies.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_J, KEY_CATEGORY));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconbuddies.hud", InputConstants.Type.KEYSYM, InputConstants.KEY_H, KEY_CATEGORY));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BuddiesSession.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BuddiesSession.onDisconnect());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				client.gui.setScreen(new BuddiesSettingsScreen(client.gui.screen()));
			}
			while (toggleKey.consumeClick()) {
				BuddiesSession.toggle();
			}
			while (hudKey.consumeClick()) {
				BuddiesConfig config = BuddiesConfig.get();
				config.hudVisible = !config.hudVisible;
				BuddiesConfig.save();
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(config.hudVisible ? "beaconbuddies.message.hud_shown" : "beaconbuddies.message.hud_hidden"));
				}
			}
			BuddiesSession.tick(client);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommand(dispatcher));

		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, BeaconBuddies.id("buddies"), new BuddiesHud());
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("buddies")
				.executes(BeaconBuddiesClient::status)
				.then(ClientCommands.literal("on").executes(ctx -> run(() -> BuddiesSession.request(true))))
				.then(ClientCommands.literal("off").executes(ctx -> run(() -> BuddiesSession.request(false))))
				.then(ClientCommands.literal("toggle").executes(ctx -> run(BuddiesSession::toggle)))
				.then(ClientCommands.literal("list").executes(BeaconBuddiesClient::list)));
	}

	private static int run(Runnable action) {
		action.run();
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		String key;
		if (!BuddiesSession.isInGame()) {
			key = "beaconbuddies.status.unavailable";
		} else if (BuddiesSession.isRelayDown()) {
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
