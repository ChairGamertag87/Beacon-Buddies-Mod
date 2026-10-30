package com.chairgamertag87.beaconbuddies.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.apache.commons.lang3.StringUtils;

public final class TeamChat {
	public static final int MAX_LENGTH = 2048;
	public static final int VANILLA_MAX_LENGTH = 256;

	private static ChatComponent component;
	private static boolean teamTab;

	private TeamChat() {
	}

	public static ChatComponent component() {
		if (component == null) {
			component = new ChatComponent(Minecraft.getInstance());
		}
		return component;
	}

	public static boolean isTeamTab() {
		return teamTab && BuddiesSession.isChatAvailable();
	}

	public static void selectTeamTab(boolean team) {
		teamTab = team;
		if (isTeamTab()) {
			component().rescaleChat();
		}
	}

	public static void leaveTeamTab() {
		teamTab = false;
	}

	public static ChatComponent chatFor(ChatComponent vanilla) {
		return isTeamTab() && Minecraft.getInstance().screen instanceof ChatScreen ? component() : vanilla;
	}

	public static String normalize(String text) {
		String clean = StringUtils.normalizeSpace(text.trim().replace("§", ""));
		return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
	}

	public static void send(String text) {
		String clean = normalize(text);
		if (!clean.isEmpty()) {
			BuddiesSession.sendChat(clean);
		}
	}

	public static void receive(String name, String text) {
		String clean = normalize(text);
		if (clean.isEmpty()) {
			return;
		}
		MutableComponent line = Component.empty()
				.append(Component.translatable("beaconbuddies.chat.prefix").withStyle(Style.EMPTY.withColor(BuddiesHud.ACCENT & 0xFFFFFF)))
				.append(" ")
				.append(Component.literal(name).withStyle(Style.EMPTY.withColor(BuddiesHud.TEXT & 0xFFFFFF)))
				.append(Component.literal(" : " + clean));
		Minecraft.getInstance().gui.getChat().addMessage(line);
		component().addMessage(line);
	}

	public static void reset() {
		teamTab = false;
		if (component != null) {
			component.clearMessages(false);
		}
	}
}
