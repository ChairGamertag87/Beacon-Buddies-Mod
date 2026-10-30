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

import java.util.ArrayList;
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
	private static final int COLUMN_GAP = 12;
	private static final int MIN_WIDTH = 110;

	private static final int ROW_STRIPE = 0x10FFFFFF;
	private static final int HEADER_BG = 0x30000000;
	private static final int NEAR = 0xFF5BD873;
	private static final int MID = 0xFFE9C54A;
	private static final int FAR = 0xFFE5574E;

	private enum Column {
		DISTANCE("beaconbuddies.hud.column.distance"),
		SESSION("beaconbuddies.hud.column.session"),
		TOTAL("beaconbuddies.hud.column.total"),
		BLOCKS("beaconbuddies.hud.column.blocks");

		private final String key;

		Column(String key) {
			this.key = key;
		}
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		BuddiesConfig config = BuddiesConfig.get();
		if (client.options.hideGui || !config.hudVisible || !BuddiesSession.isEnabled()) {
			return;
		}
		int top = config.hudPosition == BuddiesConfig.HudPosition.TOP_RIGHT ? topBelowEffects(client) : MARGIN;
		drawTable(graphics, client.font, HudTable.rows(client), MARGIN, graphics.guiWidth() - MARGIN, top, config);
	}

	public static int drawTable(GuiGraphics graphics, Font font, List<HudTable.Row> rows, int areaLeft, int areaRight, int top, BuddiesConfig config) {
		List<HudTable.Row> shown = rows.subList(0, Math.min(rows.size(), config.maxRows));
		int hidden = rows.size() - shown.size();
		List<Column> columns = columns(config);

		Component title = Component.translatable("beaconbuddies.hud.title");
		Component count = Component.literal(String.valueOf(rows.stream().filter(HudTable.Row::buddy).count()));
		Component player = Component.translatable("beaconbuddies.hud.column.player");
		Component more = Component.translatable("beaconbuddies.hud.more", hidden);

		int nameOffset = config.showHeads ? HEAD_SIZE + 4 : 0;
		int nameWidth = font.width(player) - nameOffset;
		int[] widths = new int[columns.size()];
		for (int c = 0; c < columns.size(); c++) {
			widths[c] = font.width(Component.translatable(columns.get(c).key));
		}
		for (HudTable.Row row : shown) {
			nameWidth = Math.max(nameWidth, font.width(row.name()));
			for (int c = 0; c < columns.size(); c++) {
				widths[c] = Math.max(widths[c], font.width(cell(row, columns.get(c))));
			}
		}
		int contentWidth = nameOffset + nameWidth;
		for (int w : widths) {
			contentWidth += COLUMN_GAP + w;
		}
		int titleWidth = 10 + font.width(title) + COLUMN_GAP + font.width(count);
		int width = Math.max(MIN_WIDTH, Math.max(contentWidth, titleWidth) + PADDING * 2);

		int lines = 1 + shown.size() + (hidden > 0 ? 1 : 0);
		int height = TITLE_HEIGHT + lines * ROW_HEIGHT + 4;
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

		int[] columnRight = new int[columns.size()];
		int edge = right - PADDING;
		for (int c = columns.size() - 1; c >= 0; c--) {
			columnRight[c] = edge;
			edge -= widths[c] + COLUMN_GAP;
		}

		int y = bodyTop + 1;
		int textOffset = (ROW_HEIGHT - font.lineHeight) / 2 + 1;
		graphics.fill(left, y, right, y + ROW_HEIGHT, HEADER_BG);
		graphics.drawString(font, player, left + PADDING, y + textOffset, MUTED, true);
		for (int c = 0; c < columns.size(); c++) {
			Component label = Component.translatable(columns.get(c).key);
			graphics.drawString(font, label, columnRight[c] - font.width(label), y + textOffset, MUTED, true);
		}
		y += ROW_HEIGHT + 2;

		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		for (int i = 0; i < shown.size(); i++) {
			HudTable.Row row = shown.get(i);
			if (i % 2 == 1) {
				graphics.fill(left, y, right, y + ROW_HEIGHT, ROW_STRIPE);
			}
			int x = left + PADDING;
			if (config.showHeads) {
				PlayerFaceRenderer.draw(graphics, skinOf(connection, row.uuid()), x, y + (ROW_HEIGHT - HEAD_SIZE) / 2, HEAD_SIZE);
			}
			graphics.drawString(font, row.name(), x + nameOffset, y + textOffset, nameColor(row), true);
			for (int c = 0; c < columns.size(); c++) {
				String value = cell(row, columns.get(c));
				graphics.drawString(font, value, columnRight[c] - font.width(value), y + textOffset, cellColor(row, columns.get(c), config), true);
			}
			y += ROW_HEIGHT;
		}
		if (hidden > 0) {
			graphics.drawString(font, more, left + PADDING, y + textOffset, MUTED, true);
		}
		return top + height + 1;
	}

	public static void drawTitleBar(GuiGraphics graphics, Font font, int left, int right, int top, Component title, Component trailing) {
		graphics.fill(left, top, right, top + TITLE_HEIGHT, TITLE_BG);
		graphics.hLine(left, right - 1, top, TITLE_HIGHLIGHT);
		int textY = top + (TITLE_HEIGHT - font.lineHeight) / 2 + 1;
		drawDiamond(graphics, left + PADDING + 2, top + TITLE_HEIGHT / 2);
		graphics.drawString(font, title, left + PADDING + 8, textY, TEXT, true);
		graphics.drawString(font, trailing, right - PADDING - font.width(trailing), textY, ACCENT, true);
	}

	private static List<Column> columns(BuddiesConfig config) {
		List<Column> columns = new ArrayList<>();
		columns.add(Column.DISTANCE);
		if (config.showPlaytime) {
			columns.add(Column.SESSION);
			columns.add(Column.TOTAL);
		}
		if (config.showBlocks) {
			columns.add(Column.BLOCKS);
		}
		return columns;
	}

	private static String cell(HudTable.Row row, Column column) {
		return switch (column) {
			case DISTANCE -> {
				if (!row.buddy()) {
					yield "";
				}
				yield row.otherDimension()
						? Component.translatable("beaconbuddies.hud.other_dimension").getString()
						: Component.translatable("beaconbuddies.hud.distance", HudTable.formatCount(Math.round(row.distance()))).getString();
			}
			case SESSION -> row.sessionSeconds() >= 0 ? PlaytimeList.format(row.sessionSeconds()) : "";
			case TOTAL -> row.totalSeconds() >= 0 ? PlaytimeList.format(row.totalSeconds()) : "";
			case BLOCKS -> row.placed() >= 0 ? HudTable.formatCount(row.placed()) : "";
		};
	}

	private static int cellColor(HudTable.Row row, Column column, BuddiesConfig config) {
		return switch (column) {
			case DISTANCE -> distanceColor(row, config);
			case SESSION -> ACCENT;
			case TOTAL -> MUTED;
			case BLOCKS -> TEXT;
		};
	}

	private static int nameColor(HudTable.Row row) {
		if (row.self()) {
			return ACCENT;
		}
		return row.otherDimension() ? MUTED : TEXT;
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

	private static int distanceColor(HudTable.Row row, BuddiesConfig config) {
		if (row.otherDimension()) {
			return MUTED;
		}
		if (!config.distanceColors) {
			return TEXT;
		}
		if (row.distance() < 50) {
			return NEAR;
		}
		return row.distance() < 200 ? MID : FAR;
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
