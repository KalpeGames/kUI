package dev.kui.api;

import dev.kui.Kui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Every mod that has registered with KUI this session. */
public final class KuiRegistry {
    private static final List<KuiMod> MODS = new ArrayList<>();

    private KuiRegistry() {
    }

    static void add(KuiMod mod) {
        for (KuiMod existing : MODS) {
            if (existing.modId().equals(mod.modId())) {
                Kui.LOGGER.warn("[KUI] Mod {} registered twice, ignoring the second registration",
                        mod.modId());
                return;
            }
        }
        MODS.add(mod);
        MODS.sort(Comparator.comparing(KuiMod::displayName));
        Kui.LOGGER.info("[KUI] Registered mod {} ({})", mod.displayName(), mod.modId());
    }

    public static List<KuiMod> mods() {
        return Collections.unmodifiableList(MODS);
    }

    public static KuiMod byId(String modId) {
        return MODS.stream().filter(m -> m.modId().equals(modId)).findFirst().orElse(null);
    }
}
