package com.chairgamertag87.beaconbuddies.client.relay;

import java.util.UUID;

public record PlayerStat(UUID uuid, String name, long totalSeconds, long sessionStart) {
}
