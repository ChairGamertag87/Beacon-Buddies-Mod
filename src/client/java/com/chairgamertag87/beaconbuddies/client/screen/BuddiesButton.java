package com.chairgamertag87.beaconbuddies.client.screen;

import com.chairgamertag87.beaconbuddies.client.BuddiesHud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class BuddiesButton extends AbstractWidget {
	static final int BG = 0xFF1B2023;
	static final int BG_HOVER = 0xFF223035;
	static final int BG_DISABLED = 0xFF151819;
	static final int BORDER_HOVER = 0xFF2BB5A8;

	private final Supplier<Component> label;
	private final IntSupplier labelColor;
	private final IntSupplier swatch;
	private final Runnable onPress;

	public BuddiesButton(int x, int y, int width, int height, Supplier<Component> label, IntSupplier labelColor, IntSupplier swatch, Runnable onPress) {
		super(x, y, width, height, label.get());
		this.label = label;
		this.labelColor = labelColor;
		this.swatch = swatch;
		this.onPress = onPress;
	}

	public BuddiesButton(int x, int y, int width, int height, Supplier<Component> label, Runnable onPress) {
		this(x, y, width, height, label, () -> BuddiesHud.TEXT, null, onPress);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Component text = label.get();
		setMessage(text);
		boolean highlighted = active && isHoveredOrFocused();
		graphics.fill(getX(), getY(), getRight(), getBottom(), !active ? BG_DISABLED : highlighted ? BG_HOVER : BG);
		graphics.renderOutline(getX(), getY(), width, height, highlighted ? BORDER_HOVER : BuddiesHud.BORDER);

		Font font = Minecraft.getInstance().font;
		int textWidth = font.width(text);
		int swatchSize = swatch != null ? 7 : 0;
		int contentWidth = textWidth + (swatchSize > 0 ? swatchSize + 4 : 0);
		int x = getX() + (width - contentWidth) / 2;
		int textY = getY() + (height - font.lineHeight) / 2 + 1;
		if (swatch != null) {
			int sy = getY() + (height - swatchSize) / 2;
			graphics.fill(x, sy, x + swatchSize, sy + swatchSize, 0xFF000000 | swatch.getAsInt());
			graphics.renderOutline(x - 1, sy - 1, swatchSize + 2, swatchSize + 2, BuddiesHud.BORDER);
			x += swatchSize + 4;
		}
		graphics.drawString(font, text, x, textY, active ? labelColor.getAsInt() : BuddiesHud.MUTED, true);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onPress.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
