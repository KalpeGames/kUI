package dev.kui.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinding registration that works across the versions KUI targets.
 *
 * <p>1.21.9 replaced the plain {@code String} keybind category with a {@code KeyBinding.Category}
 * object keyed by an {@link net.minecraft.util.Identifier}. Mods register through here so that
 * difference is handled once.
 */
public final class Keys {
    private Keys() {
    }

    /**
     * Registers a rebindable key under the {@code namespace} mod's own category in
     * Options &gt; Controls.
     *
     * @param namespace      the owning mod id, which also names the category
     * @param translationKey full translation key for the binding, e.g. {@code key.kui.hud_layout}
     * @param defaultKey     a GLFW key code, or {@link GLFW#GLFW_KEY_UNKNOWN} to leave it unbound
     */
    public static KeyBinding register(String namespace, String translationKey, int defaultKey) {
        //? if >=1.21.11 {
        // Category label resolves to "key.category.<namespace>.main".
        KeyBinding.Category category =
                KeyBinding.Category.create(net.minecraft.util.Identifier.of(namespace, "main"));
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, category));
        //?} else {
        /*// Category label resolves to the literal string, i.e. "category.<namespace>".
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, "category." + namespace));
        *///?}
    }

    /** True while either control key is held. Used for modifier-aware scroll handling. */
    public static boolean isCtrlDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** True while either shift key is held. */
    public static boolean isShiftDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
