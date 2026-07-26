<img src="https://raw.githubusercontent.com/KalpeGames/kUI/main/src/main/resources/assets/kui/icon.png" width="96" align="right" alt="kUI">

## One screen to arrange every HUD in your game

Press **K**. Drag the hotbar somewhere better. Shrink the scoreboard. Hide the crosshair. Move a
mod's card out from under your health bar. Close the screen — it stays that way.

<br clear="right">

![Description](https://raw.githubusercontent.com/KalpeGames/kUI/main/docs/banners/description.png)

kUI is the shared interface layer behind the KALPE mods. Every HUD element from every kUI mod lands
in one registry, draws through one render hook, and is arranged in one editor. Two mods can no
longer drop their cards on top of each other, and the order things draw in is a number you control
rather than an accident of which mod happened to load first.

**Vanilla's HUD is in there too.** The hotbar, health, hunger, armour, air, mount health, XP bar and
level, held-item name, action bar, crosshair, title text, subtitles, boss bar, status effects,
scoreboard, player list and chat can all be moved, resized and hidden — no mixins, no resource pack,
and nothing stopping the game from putting them back exactly where they were.

The selection box around a vanilla element is *measured*, not guessed: kUI runs the element's own
renderer against a throwaway state and takes the true bounds of what it drew. The box fits the
scoreboard you actually have, with the lines it actually has.

### In the editor

| | |
| --- | --- |
| **Drag** | Move anything |
| **Scroll** | Resize |
| **Ctrl + scroll** | Fade a mod's element |
| **Right-click** | Show / hide |
| **Middle-click** | Cycle an element's look, where it has more than one |
| **Ctrl + click** | Multi-select, then group |
| **Double-click** | Step inside a group to edit one member |
| **Arrow keys** | Nudge 1px, or 10 with Shift |

Grouped elements move as one — the hotbar and the bars welded to it come pre-grouped, because
moving the hotbar without your health bar is almost never what anyone wants.

Elements you leave **docked** stack automatically into a lane at their corner, so they cannot
overlap no matter how many mods you add. Drag one anywhere and it switches to **free** placement,
stored as an offset from the nearest corner so it stays put when you resize the window.

### One accent, everywhere

Pick a colour in the editor and every kUI mod follows it — panels, borders, highlights, HUD cards.
Mods can carry their own accent where their identity matters, and you can override that too. Global
UI scale, per-element scale and per-element opacity all live in the same place.

Your arrangement is stored in `config/kui/layout.json`, keyed by mod, so a mod you uninstall never
disturbs the rest and any single element can be reset on its own.

### Requirements

Fabric, client-side only. Needs Fabric API. Built for **1.21.8** and **1.21.11** — grab the file
matching your version.

> On its own, kUI gives you the vanilla-HUD editor. Install any mod built on it and that mod's
> elements appear in the same screen, arranged alongside everything else.

![Development](https://raw.githubusercontent.com/KalpeGames/kUI/main/docs/banners/development.png)

Building a Fabric mod with a HUD? kUI hands you the registry, the layout engine, the editor, the
theme and the drawing primitives, so you write the element and nothing else:

```java
KuiMod.of("mymod", "My Mod")
      .accent(0xFF9B3D)
      .settings(MySettingsScreen::new)
      .element(new MyElement())
      .register();
```

That is the whole integration. Your element gets dragging, resizing, fading, grouping, z-ordering
against other mods' elements, and a place in the shared editor — none of which you have to write,
and all of which behaves identically to every other kUI mod on the profile.

Draw with `Theme.accent(modId)` and `Draw.roundedCard` instead of hardcoded colours and rectangles,
and your HUD follows the user's chosen accent for free.

**[Full integration guide on GitHub →](https://github.com/KalpeGames/kUI)**

Five steps, with copy-paste templates for the one fiddly part: handling the case where kUI is not
installed. Declare it under `recommends` rather than `depends`, keep your `dev.kui` references
behind a single gate class, and a player without the library gets a screen explaining what to
install instead of a launch failure.

![Licence](https://raw.githubusercontent.com/KalpeGames/kUI/main/docs/banners/licence.png)

kUI is free to use and free to ship with. You may build on it, and you may redistribute it
unmodified — including bundled inside your own mod — with no permission needed and nothing to pay.

What is not allowed is publishing a modified kUI, or publishing it under another name or as your
own work. Patching your own copy privately, to debug or to prepare a fix to send upstream, is
explicitly fine.

Copyright © 2026 KALPE. **[Full licence →](https://github.com/KalpeGames/kUI/blob/main/LICENSE)**
