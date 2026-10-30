package com.chairgamertag87.beaconbuddies.client;

import com.chairgamertag87.beaconbuddies.BeaconBuddies;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.world.scores.TeamColor;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BuddiesConfig {
	public static final int MIN_ROWS = 3;
	public static final int MAX_ROWS = 15;
	public static final List<TeamColor> GLOW_COLORS = TeamColor.VALUES;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(BeaconBuddies.MOD_ID + ".json");

	private static BuddiesConfig instance = new BuddiesConfig();

	public enum HudPosition {
		TOP_RIGHT, TOP_LEFT, TOP_CENTER;

		public HudPosition next() {
			HudPosition[] values = values();
			return values[(ordinal() + 1) % values.length];
		}

		public String translationKey() {
			return "beaconbuddies.position." + name().toLowerCase(Locale.ROOT);
		}
	}

	public boolean hudVisible = true;
	public HudPosition hudPosition = HudPosition.TOP_RIGHT;
	public int maxRows = 8;
	public boolean distanceColors = true;
	public boolean showHeads = true;
	public boolean showPlaytime = true;
	public boolean showBlocks = true;
	public String glowColor = TeamColor.WHITE.getSerializedName();

	public static BuddiesConfig get() {
		return instance;
	}

	public TeamColor glowTeamColor() {
		TeamColor color = TeamColor.byName(glowColor);
		return color != null ? color : TeamColor.WHITE;
	}

	public void cycleGlowColor() {
		int index = GLOW_COLORS.indexOf(glowTeamColor());
		glowColor = GLOW_COLORS.get((index + 1) % GLOW_COLORS.size()).getSerializedName();
	}

	public static void load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				BuddiesConfig loaded = GSON.fromJson(reader, BuddiesConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				BeaconBuddies.LOGGER.warn("Config illisible, valeurs par défaut utilisées : {}", e.getMessage());
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			BeaconBuddies.LOGGER.warn("Impossible de sauvegarder la config : {}", e.getMessage());
		}
	}

	private void sanitize() {
		if (hudPosition == null) {
			hudPosition = HudPosition.TOP_RIGHT;
		}
		maxRows = Mth.clamp(maxRows, MIN_ROWS, MAX_ROWS);
		glowColor = glowTeamColor().getSerializedName();
	}
}
