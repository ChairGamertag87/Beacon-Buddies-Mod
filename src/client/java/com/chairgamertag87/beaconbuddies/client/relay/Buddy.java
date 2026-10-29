package com.chairgamertag87.beaconbuddies.client.relay;

import java.util.UUID;

public record Buddy(UUID uuid, String name, double x, double y, double z, String dimension) {
}
