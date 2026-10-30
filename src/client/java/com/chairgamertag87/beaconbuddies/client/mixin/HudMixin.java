package com.chairgamertag87.beaconbuddies.client.mixin;

import com.chairgamertag87.beaconbuddies.client.TeamChat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class HudMixin {
	@WrapOperation(method = "extractChat", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;chat:Lnet/minecraft/client/gui/components/ChatComponent;"))
	private ChatComponent beaconbuddies$showSelectedChat(Hud hud, Operation<ChatComponent> original) {
		return TeamChat.chatFor(original.call(hud));
	}
}
