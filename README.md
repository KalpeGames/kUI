# KUI

Shared client UI library for the KALPE Minecraft mods.

Each mod used to draw its own HUD through its own Fabric render hook, with its own theme and its own
"edit HUD" screen. That meant no mod could see any other mod's elements, so their HUD cards could be
placed on top of each other with nothing to prevent it, and the z-order between two mods was an
accident of which Fabric API each happened to register with.

KUI replaces all of that with one registry, one render hook and one editor.

## What it provides

| Package | What lives there |
| --- | --- |
| `dev.kui.hud` | The HUD registry, layout engine and the shared layout editor |
| `dev.kui.theme` | Semantic palette (`PANEL`, `TEXT`, `accent(modId)`…) and colour maths |
| `dev.kui.draw` | Drawing primitives and drawn icons |
| `dev.kui.compat` | Version shims — every Stonecutter guard for UI code lives here, once |
| `dev.kui.config` | `KuiConfig` (accent, UI scale, tint) and `LayoutStore` (placements) |
| `dev.kui.api` | `KuiMod` registration |

## Using it from a mod

Compile against it. KUI is installed separately, the way Fabric API is, so every mod on a profile
shares one copy and a KUI fix reaches all of them without re-releasing each mod:

```kotlin
repositories { mavenLocal() }

dependencies {
    // No include(): KUI is not bundled. One copy, installed by the user.
    modImplementation("dev.kui:kui:1.1.0+mc${property("minecraft_version")}")
}
```

Declare it in `fabric.mod.json` as a **recommendation**, not a dependency:

```json
"recommends": { "kui": ">=1.1.0" }
```

`depends` would be simpler, but it hands the failure to Fabric: a missing or outdated KUI stops the
game before it starts, and the player reads loader output instead of anything you wrote. Under
`recommends` your mod loads and you decide what to say — see below.

### Surviving without it

Because KUI can now genuinely be absent, keep every reference to `dev.kui` in classes you can
choose not to touch. The JVM resolves a class the first time a method mentioning it runs, so a
check must live outside the code it guards:

```java
if (KuiGate.satisfied()) {
    MyModKui.register();      // the only class importing dev.kui.*
} else {
    KuiGate.armNotice();      // vanilla-only screen, no KUI imports anywhere in it
}
```

Version matters as much as presence: raise the version your gate requires in the same commit that
first uses a newly added KUI API, or a player running an older KUI gets a `NoSuchMethodError`
mid-game rather than a sentence explaining what to update.

Write an element:

```java
public class MyElement extends HudElement {
    public MyElement() {
        super(Identifier.of("mymod", "status"), "Status");
    }

    @Override protected Placement defaults() { return new Placement(Anchor.TOP_RIGHT, 100); }
    @Override public int width()  { return 80; }
    @Override public int height() { return 14; }
    @Override public boolean hasContent() { return somethingWorthShowing(); }

    @Override public void render(DrawContext ctx, int x, int y, float scale, boolean editing) {
        Draw.pushScale(ctx, x, y, scale);
        Draw.roundedCard(ctx, 0, 0, width(), height(), panelBg(), cardBorder());
        Draw.text(ctx, "Hello", 4, 3, Theme.TEXT);
        Draw.pop(ctx);
    }
}
```

Register it during client init:

```java
KuiMod.of("mymod", "My Mod")
      .accent(0xFF9B3D)
      .settings(MySettingsScreen::new)
      .element(new MyElement())
      .register();
```

That is the whole integration. Do **not** register a HUD render hook — KUI owns the only one, which
is what makes cross-mod ordering and collision-free stacking possible.

## Vanilla HUD elements

KUI also makes vanilla's own HUD arrangeable — hotbar, health, hunger, armour, air, mount health,
XP bar and level, held-item name, action bar text, crosshair, title text, subtitles, boss bar,
status effects, scoreboard, player list and chat. They appear in the editor under a `minecraft`
group alongside your mods' elements and can be moved, resized and hidden.

This works through `HudElementRegistry.replaceElement`, wrapping vanilla's own renderer in a
transform — no mixins. What gets stored is a **delta from wherever vanilla would have drawn the
element**, not an absolute position, so the arrangement survives resizes and version changes to
vanilla's own layout.

The selection box around a vanilla element is *measured*, not guessed. Since 1.21.6 a `DrawContext`
does not touch the GPU; it appends elements to a `GuiRenderState`. So while the editor is open,
`BoundsProbe` runs each vanilla renderer a second time against a throwaway state and takes the
union of everything it appended — the element's true bounding box for this frame's contents, with
no per-element knowledge of vanilla's layout and nothing to keep up to date when that layout
changes. The box therefore fits the scoreboard that is actually on screen, with the lines it
actually has.

Two consequences worth knowing:

- The nominal rectangles in `VanillaHud` are only the fallback, used for an element that is drawing
  nothing at all right now (no boss, no sidebar objective) — there is no box to measure, but the
  user still needs somewhere to grab. The editor labels those "not on screen".
- Vanilla elements cannot be docked into a lane (their position is vanilla's, not KUI's) and cannot
  be faded (they draw with their own colours). The inspector hides those controls for them.

## Placement

Every element has a `Placement` with one of two modes:

- **Docked** (default) — the element declares an anchor, and the manager stacks everything sharing
  that anchor into a lane with a gap. Nothing in a lane can overlap, with no user effort. Lanes are
  inset past the regions vanilla owns (hotbar and status bars, status-effect icons, the chat box).
- **Free** — the user dragged it somewhere specific. Position is stored as a pixel offset from the
  nearest anchor, so it stays welded to that corner when the window resizes or the element's own
  size changes.

`order` is both the sort key within a lane and the global draw order, so a low-order element from
one mod really does render behind a high-order element from another.

Placements live in `config/kui/layout.json`, keyed by namespaced element id. KUI deliberately owns
only presentation state — each mod keeps its own config file for its own settings, so uninstalling
one mod cannot corrupt another's configuration.

## Building

Multi-version via [Stonecutter](https://stonecutter.kikugie.dev), currently targeting Minecraft
1.21.8 and 1.21.11.

```sh
./gradlew build                    # the active target
./gradlew chiseledBuild            # every target
./gradlew publishAllToMavenLocal   # every target into ~/.m2, for the consuming mods
```

The targets here must be a superset of the targets of every mod that depends on KUI, since a mod can
only bundle a KUI build for the version it is itself compiling against.

Consuming mods resolve `dev.kui:kui:<version>+mc<minecraft_version>`, so after changing anything in
this project run `publishAllToMavenLocal` before rebuilding them.

## Version compatibility

The APIs that moved between 1.21.8 and 1.21.11 are handled here rather than in each mod:

| What changed | Where it is handled |
| --- | --- |
| `KeyBinding.Category` object vs `String` category | `compat/Keys` |
| `Click` / `KeyInput` screen input signatures | `hud/HudLayoutScreen` |
| `DrawContext` constructor gained a cursor position | `compat/GuiProbe` |

`HudElementRegistry` and `VanillaHudElements` turn out to be identical across both targets, so
`compat/HudHook` needs no guard at all.

## Licence

Copyright © 2026 KALPE. See [LICENSE](LICENSE) — this is **not** an open-source licence.

You may build a mod on KUI, and redistribute KUI unmodified — including **bundled inside your own
mod** (Jar-in-Jar) if you prefer that to asking users to install it — at no cost and with no
permission needed. The KALPE mods ship it as a separate download instead, but the grant covers
both, so a mod that would rather bundle it is not blocked.

What is not allowed is publishing a modified KUI, or publishing it under another name or as your
own work. Patching your own copy privately — to debug, or to prepare a fix to send back here — is
explicitly fine; distributing that patched copy is not.
