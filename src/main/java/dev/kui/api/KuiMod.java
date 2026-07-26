package dev.kui.api;

import dev.kui.config.KuiConfig;
import dev.kui.hud.HudElement;
import dev.kui.hud.HudManager;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Function;

/**
 * A mod's registration with KUI.
 *
 * <p>Registering is what puts a mod's name in the layout editor's sidebar, gives it an accent
 * colour of its own, and (later, once the hub screen lands) lists it in one place alongside every
 * other KUI mod.
 *
 * <pre>{@code
 * KuiMod.of("spotifymod", "Spotify")
 *       .accent(0x1DB954)
 *       .settings(SettingsScreen::new)
 *       .element(new NowPlayingElement())
 *       .element(new LyricsElement())
 *       .register();
 * }</pre>
 */
public final class KuiMod {
    private final String modId;
    private final String displayName;
    private Integer accent;
    private Function<Screen, Screen> settingsFactory;

    private KuiMod(String modId, String displayName) {
        this.modId = modId;
        this.displayName = displayName;
    }

    public static KuiMod of(String modId, String displayName) {
        return new KuiMod(modId, displayName);
    }

    /**
     * Sets this mod's accent as 0xRRGGBB. Applied only the first time the mod is seen, so a user
     * who picks their own accent keeps it across updates.
     */
    public KuiMod accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    /** Supplies this mod's settings screen, given the screen to return to on close. */
    public KuiMod settings(Function<Screen, Screen> factory) {
        this.settingsFactory = factory;
        return this;
    }

    /** Adds a HUD element to the shared registry. */
    public KuiMod element(HudElement element) {
        HudManager.register(element);
        return this;
    }

    public String modId() {
        return modId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean hasSettings() {
        return settingsFactory != null;
    }

    public Screen settingsScreen(Screen parent) {
        return settingsFactory == null ? null : settingsFactory.apply(parent);
    }

    /** Completes registration. Call once, during client init. */
    public KuiMod register() {
        if (accent != null) {
            KuiConfig config = KuiConfig.get();
            if (!config.modAccents.containsKey(modId)) {
                config.setAccentFor(modId, accent);
                config.save();
            }
        }
        KuiRegistry.add(this);
        return this;
    }
}
