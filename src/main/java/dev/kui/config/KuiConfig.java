package dev.kui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kui.Kui;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Cross-cutting UI settings shared by every mod built on KUI, stored in
 * {@code .minecraft/config/kui/settings.json}.
 *
 * <p>KUI deliberately owns <em>only</em> presentation state: accent colour, global scale, card
 * tinting. Each mod keeps its own config file for its own domain settings, so uninstalling one mod
 * can never corrupt another's configuration. HUD placement lives separately in
 * {@link LayoutStore}.
 */
public final class KuiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Config root for everything KUI owns. Public so {@link LayoutStore} shares it. */
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(Kui.MOD_ID);
    private static final Path PATH = DIR.resolve("settings.json");

    private static KuiConfig instance;

    /** KUI's own accent: the blue every shared screen is themed around. */
    public static final int DEFAULT_ACCENT = 0x1B91F3;

    /** The accent KUI shipped with before it was themed blue. */
    private static final int LEGACY_ACCENT = 0x1DB954;

    /** Default accent as 0xRRGGBB, used by any mod that has not set its own. */
    public int accentColor = DEFAULT_ACCENT;

    /** Blend panel/card backgrounds slightly toward the accent colour. */
    public boolean accentTintCards = false;

    /** Global multiplier applied on top of each element's own scale. */
    public double uiScale = 1.0;

    /** Per-mod accent overrides, keyed by mod id, as 0xRRGGBB. */
    public Map<String, Integer> modAccents = new HashMap<>();

    /** Draw a thin outline around every HUD element (useful when arranging a busy screen). */
    public boolean debugOutlines = false;

    /** Where the layout editor's inspector panel was left. -1 means "the default corner". */
    public int inspectorX = -1;
    public int inspectorY = -1;

    public static KuiConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** The accent for {@code modId} as 0xRRGGBB, falling back to the global accent. */
    public int accentFor(String modId) {
        Integer own = modAccents.get(modId);
        return own != null ? own & 0xFFFFFF : accentColor & 0xFFFFFF;
    }

    public void setAccentFor(String modId, int rgb) {
        modAccents.put(modId, rgb & 0xFFFFFF);
    }

    public void clearAccentFor(String modId) {
        modAccents.remove(modId);
    }

    private static KuiConfig load() {
        try {
            if (Files.exists(PATH)) {
                KuiConfig cfg = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), KuiConfig.class);
                if (cfg != null) {
                    if (cfg.modAccents == null) {
                        cfg.modAccents = new HashMap<>();
                    }
                    // A config written before KUI was themed blue carries the old default. Nobody
                    // chose that colour, they just installed early, so move it on. Per-mod accents
                    // are left alone: those are a mod's own identity, not KUI's theme.
                    if ((cfg.accentColor & 0xFFFFFF) == LEGACY_ACCENT) {
                        cfg.accentColor = DEFAULT_ACCENT;
                    }
                    cfg.uiScale = Math.max(0.5, Math.min(2.5, cfg.uiScale));
                    return cfg;
                }
            }
        } catch (Exception e) {
            Kui.LOGGER.error("[KUI] Failed to load settings, using defaults", e);
        }
        return new KuiConfig();
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Kui.LOGGER.error("[KUI] Failed to save settings", e);
        }
    }
}
