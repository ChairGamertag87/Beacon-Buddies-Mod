package com.chairgamertag87.beaconbuddies.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;
import java.util.UUID;

public class BuddiesHud implements HudElement {
	public static final int ACCENT = 0xFF7FF5E8;
	public static final int TITLE_BG = 0xF0105E5A;
	public static final int TITLE_HIGHLIGHT = 0xFF2BB5A8;
	public static final int BODY_BG = 0xC0101315;
	public static final int BORDER = 0xFF2C3438;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int MUTED = 0xFF8A9499;
	public static final int TITLE_HEIGHT = 13;

	private static final int MARGIN = 4;
	private static final int EFFECT_ROW_HEIGHT = 26;
	private static final int PADDING = 5;
	private static final int ROW_HEIGHT = 12;
	private static final int HEAD_SIZE = 8;
	private static final int COLUMN_GAP = 14;
	private static final int MIN_WIDTH = 110;

	private static final int ROW_STRIPE = 0x10FFFFFF;
	private static final int NEAR = 0xFF5BD873;
	private static final int MID = 0xFFE9C54A;
	private static final int FAR = 0xFFE5574E;

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		BuddiesConfig config = BuddiesConfig.get();
		if (client.options.hideGui || !config.hudVisible || !BuddiesSession.isEnabled()) {
			return;
		}
		int top = config.hudPosition == BuddiesConfig.HudPosition.TOP_RIGHT ? topBelowEffects(client) : MARGIN;
		drawPanel(graphics, client.font, BuddyList.sorted(client), MARGIN, graphics.guiWidth() - MARGIN, top, config);
	}

	public static void drawPanel(GuiGraphics graphics, Font font, List<BuddyList.Entry> entries, int areaLeft, int areaRight, int top, BuddiesConfig config) {
		List<BuddyList.Entry> shown = entries.subList(0, Math.min(entries.size(), config.maxRows));
		int hidden = entries.size() - shown.size();

		Component title = Component.translatable("beaconbuddies.hud.title");
		Component count = Component.literal(String.valueOf(entries.size()));
		Component empty = Component.translatable("beaconbuddies.hud.empty");
		Component more = Component.translatable("beaconbuddies.hud.more", hidden);

		int nameOffset = config.showHeads ? HEAD_SIZE + 4 : 0;
		int nameWidth = 0;
		int distanceWidth = 0;
		for (BuddyList.Entry entry : shown) {
			nameWidth = Math.max(nameWidth, font.width(entry.buddy().name()));
			distanceWidth = Math.max(distanceWidth, font.width(distanceLabel(entry)));
		}
		int contentWidth = shown.isEmpty() ? font.width(empty) : nameOffset + nameWidth + COLUMN_GAP + distanceWidth;
		int titleWidth = 10 + font.width(title) + COLUMN_GAP + font.width(count);
		int width = Math.max(MIN_WIDTH, Math.max(contentWidth, titleWidth) + PADDING * 2);

		int rows = Math.max(shown.size(), 1) + (hidden > 0 ? 1 : 0);
		int height = TITLE_HEIGHT + rows * ROW_HEIGHT + 4;
		int left = switch (config.hudPosition) {
			case TOP_LEFT -> areaLeft;
			case TOP_CENTER -> (areaLeft + areaRight - width) / 2;
			case TOP_RIGHT -> areaRight - width;
		};
		int right = left + width;
		int bodyTop = top + TITLE_HEIGHT;

		drawTitleBar(graphics, font, left, right, top, title, count);
		graphics.fill(left, bodyTop, right, top + height, BODY_BG);
		graphics.renderOutline(left - 1, top - 1, width + 2, height + 2, BORDER);
		graphics.hLine(left, right - 1, bodyTop, TITLE_HIGHLIGHT);

		int y = bodyTop + 3;
		if (shown.isEmpty()) {
			graphics.drawString(font, empty, left + PADDING, y + 2, MUTED, true);
			return;
		}

		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		for (int i = 0; i < shown.size(); i++) {
			BuddyList.Entry entry = shown.get(i);
			if (i % 2 == 1) {
				graphics.fill(left, y, right, y + ROW_HEIGHT, ROW_STRIPE);
			}
			int x = left + PADDING;
			int rowTextY = y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
			if (config.showHeads) {
				PlayerFaceRenderer.draw(graphics, skinOf(connection, entry.buddy().uuid()), x, y + (ROW_HEIGHT - HEAD_SIZE) / 2, HEAD_SIZE);
			}
			Component distance = distanceLabel(entry);
			graphics.drawString(font, entry.buddy().name(), x + nameOffset, rowTextY, entry.otherDimension() ? MUTED : TEXT, true);
			graphics.drawString(font, distance, right - PADDING - font.width(distance), rowTextY, distanceColor(entry, config), true);
			y += ROW_HEIGHT;
		}
		if (hidden > 0) {
			graphics.drawString(font, more, left + PADDING, y + (ROW_HEIGHT - font.lineHeight) / 2 + 1, MUTED, true);
		}
	}

	public static void drawTitleBar(GuiGraphics graphics, Font font, int left, int right, int top, Component title, Component trailing) {
		graphics.fill(left, top, right, top + TITLE_HEIGHT, TITLE_BG);
		graphics.hLine(left, right - 1, top, TITLE_HIGHLIGHT);
		int textY = top + (TITLE_HEIGHT - font.lineHeight) / 2 + 1;
		drawDiamond(graphics, left + PADDING + 2, top + TITLE_HEIGHT / 2);
		graphics.drawString(font, title, left + PADDING + 8, textY, TEXT, true);
		graphics.drawString(font, trailing, right - PADDING - font.width(trailing), textY, ACCENT, true);
	}

	private static int topBelowEffects(Minecraft client) {
		boolean beneficial = false;
		boolean harmful = false;
		if (client.player != null) {
			for (MobEffectInstance effect : client.player.getActiveEffects()) {
				if (!effect.showIcon()) {
					continue;
				}
				if (effect.getEffect().value().isBeneficial()) {
					beneficial = true;
				} else {
					harmful = true;
				}
			}
		}
		if (harmful) {
			return EFFECT_ROW_HEIGHT * 2 + MARGIN;
		}
		return beneficial ? EFFECT_ROW_HEIGHT + MARGIN : MARGIN;
	}

	private static Component distanceLabel(BuddyList.Entry entry) {
		return entry.otherDimension()
				? Component.translatable("beaconbuddies.hud.other_dimension")
				: Component.translatable("beaconbuddies.hud.distance", Math.round(entry.distance()));
	}

	private static int distanceColor(BuddyList.Entry entry, BuddiesConfig config) {
		if (entry.otherDimension()) {
			return MUTED;
		}
		if (!config.distanceColors) {
			return TEXT;
		}
		if (entry.distance() < 50) {
			return NEAR;
		}
		return entry.distance() < 200 ? MID : FAR;
	}

	private static PlayerSkin skinOf(ClientPacketListener connection, UUID uuid) {
		PlayerInfo info = connection != null ? connection.getPlayerInfo(uuid) : null;
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid);
	}

	private static void drawDiamond(GuiGraphics graphics, int cx, int cy) {
		graphics.fill(cx, cy - 2, cx + 1, cy + 3, ACCENT);
		graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, ACCENT);
		graphics.fill(cx - 2, cy, cx + 3, cy + 1, ACCENT);
	}
}
