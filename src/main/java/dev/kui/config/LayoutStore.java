package dev.kui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kui.Kui;
import dev.kui.hud.HudGroup;
import dev.kui.hud.Placement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Where every HUD element's placement lives, keyed by its namespaced id
 * ({@code spotifymod:now_playing}), in {@code .minecraft/config/kui/layout.json}.
 *
 * <p>Keeping the whole layout in one file — rather than one file per mod — is what lets the
 * manager reason about all elements together, and means the user can back up or share a single
 * arrangement covering every mod they run.
 *
 * <p>Writes are debounced: dragging an element updates its {@link Placement} every frame, but the
 * file is only written once the user settles. Call {@link #markDirty()} after a mutation and let
 * {@link #flushIfDirty()} do the write on the client tick.
 */
public final class LayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = KuiConfig.DIR.resolve("layout.json");

    /** Minimum gap between writes while a value is being dragged. */
    private static final long WRITE_INTERVAL_MS = 400;

    private static LayoutStore instance;

    /** Serialised state. Public for Gson; use the accessors from code. */
    public Map<String, Placement> placements = new HashMap<>();

    /** Named sets of elements the user moves as a unit. */
    public List<HudGroup> groups = new ArrayList<>();

    /**
     * Whether KUI's built-in groups have been created. Seeding happens once and is then recorded,
     * so a group the user deliberately ungroups does not reappear on the next launch.
     */
    public boolean seededDefaultGroups = false;

    private transient boolean dirty;
    private transient long lastWriteMs;

    public static LayoutStore get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * The stored placement for {@code key}, creating it from {@code defaults} the first time an
     * element is seen. The returned object is live — mutating it changes the layout.
     */
    public Placement placement(String key, Supplier<Placement> defaults) {
        return placements.computeIfAbsent(key, k -> defaults.get().sanitized());
    }

    /** Whether this element has ever been positioned (used to detect a first run). */
    public boolean has(String key) {
        return placements.containsKey(key);
    }

    /** Installs a placement directly, overwriting anything stored. Used by config migrations. */
    public void put(String key, Placement placement) {
        placements.put(key, placement.sanitized());
        markDirty();
    }

    /** Drops one element's placement, so it falls back to its defaults next frame. */
    public void reset(String key) {
        placements.remove(key);
        markDirty();
    }

    /** Drops every placement belonging to {@code modId}. */
    public void resetMod(String modId) {
        placements.keySet().removeIf(k -> k.startsWith(modId + ":"));
        markDirty();
    }

    public void resetAll() {
        placements.clear();
        groups.clear();
        markDirty();
    }

    // ---- groups ----------------------------------------------------------

    /** The group {@code key} belongs to, or null. An element can be in at most one group. */
    public HudGroup groupOf(String key) {
        for (HudGroup group : groups) {
            if (group.contains(key)) {
                return group;
            }
        }
        return null;
    }

    public HudGroup groupNamed(String name) {
        for (HudGroup group : groups) {
            if (group.name.equals(name)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Creates a group from {@code keys}, first removing them from any group they were already in
     * so an element never ends up in two.
     */
    public HudGroup createGroup(Collection<String> keys) {
        for (String key : keys) {
            HudGroup existing = groupOf(key);
            if (existing != null) {
                existing.members.remove(key);
            }
        }
        groups.removeIf(g -> g.members.size() < 2);

        String name = "Group";
        int n = 1;
        while (groupNamed(name) != null) {
            name = "Group " + (++n);
        }
        HudGroup group = new HudGroup(name, new ArrayList<>(keys));
        groups.add(group);
        markDirty();
        return group;
    }

    public void removeGroup(HudGroup group) {
        groups.remove(group);
        markDirty();
    }

    /**
     * Creates a built-in group the first time KUI runs, skipping any element the user has already
     * placed in a group of their own. Does nothing once {@link #seededDefaultGroups} is set.
     */
    public void seedGroup(String name, List<String> keys) {
        if (seededDefaultGroups) {
            return;
        }
        List<String> free = new ArrayList<>();
        for (String key : keys) {
            if (groupOf(key) == null) {
                free.add(key);
            }
        }
        if (free.size() >= 2 && groupNamed(name) == null) {
            groups.add(new HudGroup(name, free));
            markDirty();
        }
    }

    public void markDirty() {
        dirty = true;
    }

    /** Writes if there are pending changes and enough time has passed since the last write. */
    public void flushIfDirty() {
        if (!dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWriteMs < WRITE_INTERVAL_MS) {
            return;
        }
        save();
    }

    public void save() {
        dirty = false;
        lastWriteMs = System.currentTimeMillis();
        try {
            Files.createDirectories(KuiConfig.DIR);
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Kui.LOGGER.error("[KUI] Failed to save HUD layout", e);
        }
    }

    private static LayoutStore load() {
        try {
            if (Files.exists(PATH)) {
                LayoutStore store = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), LayoutStore.class);
                if (store != null) {
                    if (store.placements == null) {
                        store.placements = new HashMap<>();
                    }
                    store.placements.values().removeIf(p -> p == null);
                    store.placements.values().forEach(Placement::sanitized);
                    if (store.groups == null) {
                        store.groups = new ArrayList<>();
                    }
                    store.groups.removeIf(g -> g == null);
                    store.groups.forEach(HudGroup::sanitized);
                    // A group of one is just an element; drop them rather than render an
                    // interaction that behaves identically to selecting the member.
                    store.groups.removeIf(g -> g.members.size() < 2);
                    return store;
                }
            }
        } catch (Exception e) {
            Kui.LOGGER.error("[KUI] Failed to load HUD layout, starting from defaults", e);
        }
        return new LayoutStore();
    }
}
