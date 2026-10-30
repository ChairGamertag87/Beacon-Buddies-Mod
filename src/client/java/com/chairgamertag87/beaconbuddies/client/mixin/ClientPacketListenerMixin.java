package com.chairgamertag87.beaconbuddies.client.mixin;

import com.chairgamertag87.beaconbuddies.client.BlockStats;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleAwardStats", at = @At("TAIL"))
	private void beaconbuddies$countPlacedBlocks(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
		BlockStats.onStatsReceived();
	}
}
