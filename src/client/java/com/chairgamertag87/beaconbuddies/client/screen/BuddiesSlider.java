package com.chairgamertag87.beaconbuddies.client.screen;

import com.chairgamertag87.beaconbuddies.client.BuddiesHud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public class BuddiesSlider extends AbstractWidget {
	private static final int FILL = 0xFF105E5A;

	private final int min;
	private final int max;
	private final IntSupplier value;
	private final IntConsumer onChange;
	private final IntFunction<Component> label;

	public BuddiesSlider(int x, int y, int width, int height, int min, int max, IntSupplier value, IntConsumer onChange, IntFunction<Component> label) {
		super(x, y, width, height, label.apply(value.getAsInt()));
		this.min = min;
		this.max = max;
		this.value = value;
		this.onChange = onChange;
		this.label = label;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int current = value.getAsInt();
		Component text = label.apply(current);
		setMessage(text);
		boolean highlighted = isHoveredOrFocused();
		graphics.fill(getX(), getY(), getRight(), getBottom(), highlighted ? BuddiesButton.BG_HOVER : BuddiesButton.BG);

		int fillRight = getX() + Math.round((width - 1) * (current - min) / (float) (max - min));
		graphics.fill(getX(), getY(), fillRight, getBottom(), FILL);
		graphics.fill(fillRight - 1, getY(), fillRight + 1, getBottom(), BuddiesHud.ACCENT);
		graphics.outline(getX(), getY(), width, height, highlighted ? BuddiesButton.BORDER_HOVER : BuddiesHud.BORDER);

		Font font = Minecraft.getInstance().font;
		graphics.text(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - font.lineHeight) / 2 + 1, BuddiesHud.TEXT, true);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		setFromMouse(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		setFromMouse(event.x());
	}

	private void setFromMouse(double mouseX) {
		double ratio = Mth.clamp((mouseX - getX()) / (width - 1), 0.0, 1.0);
		int next = min + (int) Math.round(ratio * (max - min));
		if (next != value.getAsInt()) {
			onChange.accept(next);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
