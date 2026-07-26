package dev.kui.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * A named set of HUD elements that move together — "bottom bar" holding the hotbar, health, hunger
 * and the experience bar, say.
 *
 * <p>A group deliberately carries no transform of its own. Dragging one applies the same delta to
 * every member's placement immediately, so there is only ever one source of truth for where an
 * element sits. That keeps a grouped element's saved position meaningful on its own: ungrouping,
 * or editing a member individually, needs no unwinding of a parent transform.
 */
public class HudGroup {
    public String name;
    public List<String> members = new ArrayList<>();

    public HudGroup() {
    }

    public HudGroup(String name, List<String> members) {
        this.name = name;
        this.members = new ArrayList<>(members);
    }

    public boolean contains(String key) {
        return members.contains(key);
    }

    /** Repairs anything a hand-edited config might have got wrong. */
    public HudGroup sanitized() {
        if (name == null || name.isBlank()) {
            name = "Group";
        }
        if (members == null) {
            members = new ArrayList<>();
        }
        members.removeIf(m -> m == null || m.isBlank());
        return this;
    }
}
