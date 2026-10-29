package com.chairgamertag87.beaconbuddies.client.mixin;

import com.chairgamertag87.beaconbuddies.client.BuddyGlow;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int beaconbuddies$buddyOutlineColor(Entity entity, Operation<Integer> original) {
		if (!entity.isCurrentlyGlowing() && BuddyGlow.shouldGlow(entity)) {
			return BuddyGlow.color();
		}
		return original.call(entity);
	}
}
