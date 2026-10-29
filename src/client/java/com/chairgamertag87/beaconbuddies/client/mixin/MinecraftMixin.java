package com.chairgamertag87.beaconbuddies.client.mixin;

import com.chairgamertag87.beaconbuddies.client.BuddyGlow;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void beaconbuddies$glowBuddies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (BuddyGlow.shouldGlow(entity)) {
			cir.setReturnValue(true);
		}
	}
}
