package your.mod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.gui.screen.TitleScreen;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decides whether kUI is usable, and tells the player when it is not.
 *
 * <p>Copy this file and {@code KuiMissingScreen} into your mod and change the package. They are
 * deliberately not part of the kUI jar: they have to keep working when kUI is the thing missing.
 *
 * <p>kUI is declared under {@code recommends} rather than {@code depends}. Under {@code depends},
 * a missing library stops the game before it starts and the player reads Fabric's output instead
 * of anything written here; your mod would rather load, keep doing whatever is its own, and say
 * plainly what is switched off.
 *
 * <p>Nothing in this class touches a {@code dev.kui} type, which is load-bearing rather than tidy:
 * the JVM resolves a class the first time a method mentioning it runs, so a check sitting in the
 * same class as the code it guards would fail before it could report anything. Every kUI reference
 * in your mod must live in classes you can choose not to touch.
 *
 * <h2>Speaking for every mod at once</h2>
 *
 * <p>Several KALPE mods hit this at the same time, and each one popping its own screen in turn is
 * miserable. They cannot share a helper class to coordinate through — the shared library is
 * precisely what is missing — so they use the one channel two unrelated mods always have: JVM
 * system properties. Each mod adds a line to {@link #WAITING}, the first to reach the title screen
 * claims {@link #CLAIMED} and shows one screen listing all of them, and the rest stay quiet.
 */
public final class KuiGate {
    /**
     * The oldest kUI this build works against. Raise it in the same commit that first uses a newly
     * added kUI API, or this check silently stops meaning anything.
     */
    private static final String REQUIRED = "1.1.0";

    /** Where the player is sent to get it. */
    public static final String DOWNLOAD_URL = "https://modrinth.com/project/kui";

    /** Newline-separated list of "<mod> — <what is off>", shared across every KALPE mod. */
    private static final String WAITING = "kalpe.kui.waiting";
    /** Set by whichever mod shows the notice, so the others do not show a second one. */
    private static final String CLAIMED = "kalpe.kui.notified";

    private static Boolean satisfied;
    private static String installed;

    private KuiGate() {
    }

    /** True when a kUI new enough for this build is loaded. */
    public static boolean satisfied() {
        if (satisfied == null) {
            resolve();
        }
        return satisfied;
    }

    /** The loaded kUI version, or null when none is installed. */
    public static String installedVersion() {
        satisfied();
        return installed;
    }

    private static void resolve() {
        ModContainer container = FabricLoader.getInstance().getModContainer("kui").orElse(null);
        if (container == null) {
            installed = null;
            satisfied = false;
            return;
        }
        Version version = container.getMetadata().getVersion();
        installed = version.getFriendlyString();
        try {
            satisfied = version.compareTo(Version.parse(REQUIRED)) >= 0;
        } catch (VersionParsingException e) {
            // Our own constant is malformed. That is a bug here, not a problem with the player's
            // install, so assume the best and let them play.
            satisfied = true;
        }
    }

    /**
     * Registers this mod on the shared notice and arms it.
     *
     * @param modName          the mod, as the player knows it
     * @param disabledSummary  what stops working, in two or three words
     */
    public static void armNotice(String modName, String disabledSummary) {
        String line = modName + ": " + disabledSummary
                + (installedVersion() == null ? "" : " (needs " + REQUIRED + "+)");
        String existing = System.getProperty(WAITING, "");
        if (!Arrays.asList(existing.split("\n")).contains(line)) {
            System.setProperty(WAITING, existing.isEmpty() ? line : existing + "\n" + line);
        }
        LoggerFactory.getLogger("kui-gate").warn("kUI {} required, {} — {} degraded",
                REQUIRED, installed == null ? "none installed" : installed + " installed", modName);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (System.getProperty(CLAIMED) != null) {
                return;
            }
            // At the title screen, so the player hears about it before choosing a world rather
            // than mid-session. The in-world case is the fallback for a direct/quick-play launch,
            // which never shows a title screen at all.
            boolean atTitle = client.currentScreen instanceof TitleScreen;
            boolean idleInWorld = client.player != null && client.currentScreen == null;
            if (!atTitle && !idleInWorld) {
                return;
            }
            System.setProperty(CLAIMED, "1");
            client.setScreen(new KuiMissingScreen(client.currentScreen));
        });
    }

    /** Every mod waiting on kUI, in the order they registered. */
    public static List<String> waiting() {
        String value = System.getProperty(WAITING, "");
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
