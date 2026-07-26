package dev.kui;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the KUI library.
 *
 * <p>KUI exists so that several client mods can draw HUD elements without fighting each other.
 * Every element from every mod goes into one registry, is drawn through one render hook (so
 * z-order is a number rather than an accident of which Fabric API the mod happened to use), and
 * is laid out by one manager that keeps docked elements from overlapping.
 */
public final class Kui {
    public static final String MOD_ID = "kui";
    public static final Logger LOGGER = LoggerFactory.getLogger("KUI");

    private Kui() {
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
