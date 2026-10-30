package com.chairgamertag87.beaconbuddies.client.mixin;

import com.chairgamertag87.beaconbuddies.client.BuddiesHud;
import com.chairgamertag87.beaconbuddies.client.BuddiesSession;
import com.chairgamertag87.beaconbuddies.client.TeamChat;
import com.chairgamertag87.beaconbuddies.client.screen.BuddiesButton;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Unique
	private static final int TAB_WIDTH = 46;
	@Unique
	private static final int TAB_HEIGHT = 12;

	@Shadow
	protected EditBox input;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void beaconbuddies$addTabs(CallbackInfo ci) {
		if (!BuddiesSession.isChatAvailable()) {
			return;
		}
		beaconbuddies$applyMaxLength();
		if (TeamChat.isTeamTab()) {
			TeamChat.component().rescaleChat();
		}
		int y = height - 14 - TAB_HEIGHT - 1;
		addRenderableWidget(new BuddiesButton(2, y, TAB_WIDTH, TAB_HEIGHT, ChatScreenMixin::beaconbuddies$globalLabel,
				() -> TeamChat.isTeamTab() ? BuddiesHud.MUTED : BuddiesHud.ACCENT, null, () -> beaconbuddies$select(false)));
		addRenderableWidget(new BuddiesButton(2 + TAB_WIDTH + 2, y, TAB_WIDTH, TAB_HEIGHT, ChatScreenMixin::beaconbuddies$teamLabel,
				() -> TeamChat.isTeamTab() ? BuddiesHud.ACCENT : BuddiesHud.MUTED, null, () -> beaconbuddies$select(true)));
	}

	@Unique
	private static Component beaconbuddies$globalLabel() {
		return beaconbuddies$tabLabel("beaconbuddies.chat.global", TeamChat.globalUnread());
	}

	@Unique
	private static Component beaconbuddies$teamLabel() {
		return beaconbuddies$tabLabel("beaconbuddies.chat.team", TeamChat.unread());
	}

	@Unique
	private static Component beaconbuddies$tabLabel(String key, int unread) {
		Component name = Component.translatable(key);
		return unread > 0 ? Component.translatable("beaconbuddies.chat.unread", name, unread > 99 ? "99+" : String.valueOf(unread)) : name;
	}

	@Unique
	private void beaconbuddies$select(boolean team) {
		TeamChat.selectTeamTab(team);
		beaconbuddies$applyMaxLength();
	}

	@Inject(method = "mouseClicked", at = @At("RETURN"))
	private void beaconbuddies$keepInputFocused(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (getFocused() instanceof BuddiesButton) {
			setFocused(input);
		}
	}

	@Unique
	private void beaconbuddies$applyMaxLength() {
		input.setMaxLength(TeamChat.isTeamTab() ? TeamChat.MAX_LENGTH : TeamChat.VANILLA_MAX_LENGTH);
	}

	@WrapOperation(method = {"init", "removed", "keyPressed", "mouseScrolled", "mouseClicked", "moveInHistory", "render", "handleChatInput"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;getChat()Lnet/minecraft/client/gui/components/ChatComponent;"))
	private ChatComponent beaconbuddies$teamChat(Gui gui, Operation<ChatComponent> original) {
		return TeamChat.chatFor(original.call(gui));
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void beaconbuddies$sendToTeam(String message, boolean addToHistory, CallbackInfo ci) {
		if (!TeamChat.isTeamTab() || message.trim().startsWith("/")) {
			return;
		}
		String clean = TeamChat.normalize(message);
		if (!clean.isEmpty()) {
			if (addToHistory) {
				TeamChat.component().addRecentChat(clean);
			}
			TeamChat.send(clean);
		}
		ci.cancel();
	}
}
