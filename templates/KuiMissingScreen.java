package your.mod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;

/**
 * The "kUI is missing" notice, shown once for every affected mod at once.
 *
 * <p>Drawn with nothing but vanilla widgets, on purpose: the whole point of this screen is that
 * the shared UI library is not there to draw it. It cannot import a single {@code dev.kui} class,
 * so it also cannot use the theme, the fonts or the panels every other screen in this mod uses —
 * it will look plainer than the rest of your mod, and that is the correct trade.
 *
 * <p>It cannot install anything either. The mod graph is resolved before any of this code runs, so
 * a library dropped in now would not load until the game restarts; the honest offer is a link and
 * a clear instruction, not a progress bar.
 *
 * <p>The wording deliberately says nothing about <em>which</em> mod is speaking. Whichever one
 * happens to claim the notice is an implementation detail the player has no reason to care about;
 * what they need is the list of what is switched off and the one link that fixes all of it.
 */
public class KuiMissingScreen extends Screen {
    /** Error red. This is a broken install, and it should not read as a friendly tip. */
    private static final int ERROR = 0xFFE33030;
    private static final int PANEL_BG = 0xF00C1017;
    private static final int BODY = 0xFFE6EDF3;
    private static final int NOTE = 0xFFA9B6C4;
    private static final int FOOTNOTE = 0xFF8D9AA8;

    private static final int PANEL_W = 340;
    private static final int PAD = 16;
    private static final int MOD_LINE = 11;
    /** The explanation is background reading, so it is drawn at three-quarter size. */
    private static final float FOOT_SCALE = 0.75f;

    private final Screen parent;
    private List<String> mods = List.of();
    private List<OrderedText> explanation = List.of();
    private int panelH;
    private int panelTop;
    private int panelLeft;

    public KuiMissingScreen(Screen parent) {
        super(Text.literal("One or more mods need kUI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String installed = KuiGate.installedVersion();
        String message = installed == null
                ? "kUI is the shared UI library these mods draw their interface through. They stay "
                + "switched off until it is installed."
                : "The installed kUI (" + installed + ") is older than these mods were built "
                + "against. Updating it switches them back on.";
        // Wrapped against the width it will occupy once scaled down, so it still fills the panel.
        explanation = this.textRenderer.wrapLines(Text.literal(message),
                (int) ((PANEL_W - PAD * 2) / FOOT_SCALE));
        mods = KuiGate.waiting();

        panelH = PAD + 9 + 10                       // title
                + mods.size() * MOD_LINE + 8        // what is switched off
                + 9 + 8                             // the instruction
                + explanation.size() * 8 + 12       // the explanation, small
                + 20 + PAD;                         // buttons
        panelTop = (this.height - panelH) / 2;
        panelLeft = (this.width - PANEL_W) / 2;

        int buttonY = panelTop + panelH - PAD - 20;
        int buttonW = (PANEL_W - PAD * 2 - 8) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Get kUI"),
                        b -> Util.getOperatingSystem().open(URI.create(KuiGate.DOWNLOAD_URL)))
                .dimensions(panelLeft + PAD, buttonY, buttonW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy link"), b -> {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(KuiGate.DOWNLOAD_URL);
                    }
                })
                .dimensions(panelLeft + PAD + buttonW + 8, buttonY, buttonW, 20).build());

        // The dismiss affordance sits in the panel's own corner, where a close button belongs,
        // rather than reading as a third equal choice next to the two that actually do something.
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(panelLeft + PANEL_W - 26, panelTop + 6, 20, 20).build());
    }

    /**
     * Paints the panel.
     *
     * <p>Here rather than in {@link #render}: the background runs before the widget layer, and a
     * panel drawn after it lands on top of its own buttons and greys them out.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + panelH, PANEL_BG);
        ctx.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + 2, ERROR);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int x = panelLeft + PAD;
        int y = panelTop + PAD;
        ctx.drawTextWithShadow(this.textRenderer, this.title, x, y, 0xFFFFFFFF);
        y += 9 + 10;

        for (String mod : mods) {
            ctx.drawText(this.textRenderer, Text.literal("•"), x, y, ERROR, false);
            ctx.drawText(this.textRenderer, Text.literal(mod), x + 9, y, BODY, false);
            y += MOD_LINE;
        }
        y += 8;

        ctx.drawText(this.textRenderer, Text.literal("Restart Minecraft after installing."),
                x, y, NOTE, false);
        y += 9 + 8;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(FOOT_SCALE, FOOT_SCALE);
        int line = 0;
        for (OrderedText text : explanation) {
            ctx.drawText(this.textRenderer, text, 0, line, FOOTNOTE, false);
            line += 10;
        }
        ctx.getMatrices().popMatrix();
    }

    /** Dismissing is the whole point of the ✕ — the player is not blocked from playing. */
    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
