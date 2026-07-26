package dev.kui;

import dev.kui.compat.HudHook;
import dev.kui.compat.Keys;
import dev.kui.config.KuiConfig;
import dev.kui.config.LayoutStore;
import dev.kui.hud.HudLayoutScreen;
import dev.kui.hud.HudManager;
import dev.kui.hud.VanillaHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * KUI's client entrypoint.
 *
 * <p>This installs the <em>one</em> HUD render hook that every KUI mod draws through. Mods
 * register their elements with {@link HudManager} and never touch a Fabric rendering event
 * themselves, which is what makes cross-mod z-order and collision-free stacking possible.
 */
public class KuiClient implements ClientModInitializer {
    private static KeyBinding openLayoutKey;

    /** The rebindable "edit HUD layout" key. Unbound by default so it never clashes with vanilla. */
    public static KeyBinding openLayoutKey() {
        return openLayoutKey;
    }

    @Override
    public void onInitializeClient() {
        KuiConfig.get();
        LayoutStore.get();

        openLayoutKey = Keys.register(Kui.MOD_ID, "key.kui.hud_layout", GLFW.GLFW_KEY_UNKNOWN);

        HudHook.register(Kui.id("hud"), HudManager::render);

        // Wrap vanilla's own HUD so the hotbar, health bar, chat and the rest are arrangeable in
        // the same editor as the mods' elements. Must happen during init, while the HUD layer
        // registry is still open to modification.
        VanillaHud.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openLayoutKey.wasPressed()) {
                client.setScreen(new HudLayoutScreen(client.currentScreen));
            }
            // Dragging mutates placements every frame; this is where those writes settle.
            LayoutStore.get().flushIfDirty();
        });

        Kui.LOGGER.info("[KUI] Shared UI library initialised");
    }
}
