package com.chairgamertag87.beaconbuddies.client.screen;

import com.chairgamertag87.beaconbuddies.client.BuddiesConfig;
import com.chairgamertag87.beaconbuddies.client.BuddiesHud;
import com.chairgamertag87.beaconbuddies.client.BuddiesSession;
import com.chairgamertag87.beaconbuddies.client.BuddyList;
import com.chairgamertag87.beaconbuddies.client.relay.Buddy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class BuddiesSettingsScreen extends Screen {
	private static final int ON_COLOR = 0xFF5BD873;
	private static final int PANEL_BG = 0xE8101315;
	private static final int SECTION_LINE = 0xFF2C3438;

	private static final int PAD = 8;
	private static final int SETTINGS_WIDTH = 230;
	private static final int PREVIEW_WIDTH = 140;
	private static final int GAP = 12;
	private static final int ROW_STEP = 20;
	private static final int CONTROL_WIDTH = 100;
	private static final int CONTROL_HEIGHT = 16;
	private static final int SECTION_HEIGHT = 14;

	private static final List<BuddyList.Entry> SAMPLE = List.of(
			sample("Alex", 18),
			sample("Steve", 64),
			sample("Luna", 143),
			sample("Milo", 412),
			sample("Nova", 1280),
			sample("Sam", Double.POSITIVE_INFINITY));

	private record Row(Component label, int y) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private boolean showPreview;

	public BuddiesSettingsScreen(Screen parent) {
		super(Component.translatable("beaconbuddies.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rows.clear();
		BuddiesConfig config = BuddiesConfig.get();
		showPreview = width >= SETTINGS_WIDTH + GAP + PREVIEW_WIDTH + PAD * 2 + 16;
		panelWidth = SETTINGS_WIDTH + PAD * 2 + (showPreview ? GAP + PREVIEW_WIDTH : 0);
		panelHeight = BuddiesHud.TITLE_HEIGHT + PAD + SECTION_HEIGHT + ROW_STEP * 7 + PAD + CONTROL_HEIGHT + PAD;
		panelLeft = (width - panelWidth) / 2;
		panelTop = Math.max(4, (height - panelHeight) / 2);

		int controlX = panelLeft + PAD + SETTINGS_WIDTH - CONTROL_WIDTH;
		int y = panelTop + BuddiesHud.TITLE_HEIGHT + PAD + SECTION_HEIGHT;

		BuddiesButton mode = addRow("beaconbuddies.setting.mode", y, new BuddiesButton(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
				this::modeLabel, () -> BuddiesSession.isEnabled() ? ON_COLOR : BuddiesHud.MUTED, null, BuddiesSession::toggle));
		mode.active = BuddiesSession.isInGame();
		y += ROW_STEP;

		addRow("beaconbuddies.setting.hud_visible", y, toggle(controlX, y, () -> config.hudVisible, () -> config.hudVisible = !config.hudVisible));
		y += ROW_STEP;

		addRow("beaconbuddies.setting.hud_position", y, new BuddiesButton(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
				() -> Component.translatable(config.hudPosition.translationKey()), () -> config.hudPosition = config.hudPosition.next()));
		y += ROW_STEP;

		addRow("beaconbuddies.setting.max_rows", y, new BuddiesSlider(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
				BuddiesConfig.MIN_ROWS, BuddiesConfig.MAX_ROWS, () -> config.maxRows, value -> config.maxRows = value,
				value -> Component.translatable("beaconbuddies.setting.rows", value)));
		y += ROW_STEP;

		addRow("beaconbuddies.setting.distance_colors", y, toggle(controlX, y, () -> config.distanceColors, () -> config.distanceColors = !config.distanceColors));
		y += ROW_STEP;

		addRow("beaconbuddies.setting.show_heads", y, toggle(controlX, y, () -> config.showHeads, () -> config.showHeads = !config.showHeads));
		y += ROW_STEP;

		addRow("beaconbuddies.setting.glow_color", y, new BuddiesButton(controlX, y, CONTROL_WIDTH, CONTROL_HEIGHT,
				() -> Component.translatable("beaconbuddies.color." + config.glowFormatting().getName()),
				() -> BuddiesHud.TEXT, () -> config.glowFormatting().getColor(), config::cycleGlowColor));

		int doneY = panelTop + panelHeight - PAD - CONTROL_HEIGHT;
		addRenderableWidget(new BuddiesButton(panelLeft + panelWidth - PAD - CONTROL_WIDTH, doneY, CONTROL_WIDTH, CONTROL_HEIGHT,
				() -> CommonComponents.GUI_DONE, this::onClose));
	}

	private <T extends AbstractWidget> T addRow(String labelKey, int y, T widget) {
		rows.add(new Row(Component.translatable(labelKey), y));
		return addRenderableWidget(widget);
	}

	private BuddiesButton toggle(int x, int y, Supplier<Boolean> value, Runnable flip) {
		return new BuddiesButton(x, y, CONTROL_WIDTH, CONTROL_HEIGHT,
				() -> Component.translatable(value.get() ? "beaconbuddies.setting.on" : "beaconbuddies.setting.off"),
				() -> value.get() ? ON_COLOR : BuddiesHud.MUTED, null, flip);
	}

	private Component modeLabel() {
		if (!BuddiesSession.isInGame()) {
			return Component.translatable("beaconbuddies.setting.multiplayer_only");
		}
		if (!BuddiesSession.isRelayOpen()) {
			return Component.translatable("beaconbuddies.setting.offline");
		}
		return Component.translatable(BuddiesSession.isEnabled() ? "beaconbuddies.setting.on" : "beaconbuddies.setting.off");
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int right = panelLeft + panelWidth;
		BuddiesHud.drawTitleBar(graphics, font, panelLeft, right, panelTop, Component.translatable("beaconbuddies.hud.title"), title);
		graphics.fill(panelLeft, panelTop + BuddiesHud.TITLE_HEIGHT, right, panelTop + panelHeight, PANEL_BG);
		graphics.renderOutline(panelLeft - 1, panelTop - 1, panelWidth + 2, panelHeight + 2, BuddiesHud.BORDER);

		int sectionY = panelTop + BuddiesHud.TITLE_HEIGHT + PAD;
		int settingsLeft = panelLeft + PAD;
		drawSection(graphics, Component.translatable("beaconbuddies.screen.settings"), settingsLeft, settingsLeft + SETTINGS_WIDTH, sectionY);
		for (Row row : rows) {
			graphics.drawString(font, row.label(), settingsLeft, row.y() + (CONTROL_HEIGHT - font.lineHeight) / 2 + 1, BuddiesHud.TEXT, true);
		}

		if (showPreview) {
			int previewLeft = settingsLeft + SETTINGS_WIDTH + GAP;
			int previewRight = previewLeft + PREVIEW_WIDTH;
			drawSection(graphics, Component.translatable("beaconbuddies.screen.preview"), previewLeft, previewRight, sectionY);
			BuddiesHud.drawPanel(graphics, font, SAMPLE, previewLeft + 1, previewRight - 1, sectionY + SECTION_HEIGHT + 1, BuddiesConfig.get());
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawSection(GuiGraphics graphics, Component label, int left, int right, int y) {
		int labelWidth = font.width(label);
		int center = (left + right) / 2;
		int lineY = y + font.lineHeight / 2;
		graphics.hLine(left, center - labelWidth / 2 - 5, lineY, SECTION_LINE);
		graphics.hLine(center + labelWidth / 2 + 4, right - 1, lineY, SECTION_LINE);
		graphics.drawCenteredString(font, label, center, y, BuddiesHud.MUTED);
	}

	@Override
	public void removed() {
		BuddiesConfig.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static BuddyList.Entry sample(String name, double distance) {
		UUID uuid = UUID.nameUUIDFromBytes(("beaconbuddies:" + name).getBytes());
		return new BuddyList.Entry(new Buddy(uuid, name, 0, 0, 0, ""), distance);
	}
}
