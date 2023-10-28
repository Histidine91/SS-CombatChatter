package org.histidine.chatter.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.utils.StringHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ChatterCombatDrawer {

    // Message box parameters
    public static final boolean DRAW_BOX = false;
    public static final int PORTRAIT_WIDTH = 32;
    public static final int BOX_WIDTH = 400 + PORTRAIT_WIDTH;
    public static final int BOX_NAME_WIDTH = 144;
    public static final int BOX_OFFSET_X = 16;
    public static final int BOX_OFFSET_Y = 160;
    public static final int BOX_HEIGHT = 240;
    public static final Color BOX_COLOR = Color.CYAN;

    // Fleet intro parameters
    public static final float SPLASH_IMAGE_WIDTH = 0.5f;
    public static final float SPLASH_IMAGE_HEIGHT = 0.6f;
    public static final float SPLASH_TEXT_WIDTH = 0.5f;
    public static final float SPLASH_TEXT_HEIGHT = 0.1f;
    public static final float SPLASH_TEXT_YPOS = 0.35f;
    public static final float SPLASH_ALPHA = 0.75f;
    public static final float SPLASH_ALPHA_IMAGE = 0.35f;
    public static final float SPLASH_IMAGE_TIME_IN = 0.5f;
    public static final float SPLASH_IMAGE_TIME_OUT = 0.5f;
    public static final float SPLASH_TEXT_TIME_DELAY = 0.5f;
    public static final float SPLASH_TEXT_TIME_IN = 0.5f;
    public static final float SPLASH_TEXT_TIME_PEAK = 2.5f;
    public static final float SPLASH_TEXT_TIME_OUT = 0.5f;

    public static final Vector2f NAME_POS = new Vector2f(8, -8);
    public static final Vector2f TEXT_POS = new Vector2f(BOX_NAME_WIDTH + 16, -8);

    public static LazyFont font;
    public static LazyFont fontIntro;
    protected static boolean fontLoaded = false;

    public FleetIntro intro;

    protected ChatterCombatPlugin combatPlugin;

    protected static void loadFont() {
        fontLoaded = true;
        try
        {
            font = LazyFont.loadFont(Global.getSettings().getString("chatter_boxFont"));
            fontIntro = LazyFont.loadFont(Global.getSettings().getString("chatter_introFont"));
        }
        catch (FontException ex)
        {
            throw new RuntimeException("Failed to load font", ex);
        }
    }

    public ChatterCombatDrawer(ChatterCombatPlugin combatPlugin) {
        this.combatPlugin = combatPlugin;
    }

    /**
     * GL11 to start, when you want render text of Lazyfont.
     */
    public static void openGL11ForText()
    {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * GL11 to close, when you want render text of Lazyfont.
     */
    public static void closeGL11ForText()
    {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Draws a message line in the text box.
     * @param message
     * @param remainingHeight Available height remaining in the box. If the new message's
     * height exceeds this amount, do not draw.
     * @return Height of the message.
     */
    public float drawMessage(ChatterCombatPlugin.BoxMessage message, float remainingHeight)
    {
        float scale = Global.getSettings().getScreenScaleMult();
        float fontSize = Global.getSettings().getInt("chatter_boxFontSize") * scale;
        float boxWidth = BOX_NAME_WIDTH * scale;
        float boxWidth2 = (BOX_WIDTH - BOX_NAME_WIDTH - PORTRAIT_WIDTH - 8) * scale;

        // prepare ship name text
        float alpha = combatPlugin.engine.isUIShowingDialog() ? 0.5f : 1;
        Color color = combatPlugin.getShipNameColor(message.ship, alpha);
        String name = combatPlugin.getShipName(message.ship, false, ChatterConfig.chatterBoxOfficerMode);
        if (name == null || name.isEmpty()) name = "<unknown>";	// safety
        LazyFont.DrawableString str = font.createText(name, color, fontSize, boxWidth);

        // prepare message text
        color = message.color;
        color = new Color(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, alpha);
        LazyFont.DrawableString str2 = font.createText(message.text,
                color, fontSize, boxWidth2);

        float height = Math.max(str.getHeight(), str2.getHeight());
        if (height < 40 * scale) height = 40 * scale;
        if (height > remainingHeight) {
            return 99999;
        }

        // draw portrait;
        String spritePath = null;
        if (!message.ship.getCaptain().isDefault()) {
            spritePath = message.ship.getCaptain().getPortraitSprite();
        }
        else if (ChatterCombatPlugin.DEBUG_MODE) {
            spritePath = "graphics/portraits/portrait_ai2.png";
        }
        if (spritePath != null) {
            // pushing/popping attrib is needed to keep sprite from messing with text opacity
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            GL11.glPushMatrix();
            SpriteAPI sprite = Global.getSettings().getSprite(spritePath);
            float sizeMult = PORTRAIT_WIDTH/sprite.getWidth() * scale;
            float sizeMult2 = sprite.getWidth()/128 * scale;
            GL11.glScalef(sizeMult, sizeMult, 1);
            //sprite.setWidth(128);
            //sprite.setHeight(128);
            sprite.setAlphaMult(alpha);
            // FIXME: vertical padding on portraits is not consistent between GUI scalings
            // whether we render with offset or translate down and then up
            sprite.render(-128 * sizeMult2, -160 * sizeMult2);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        Vector2f namePos = new Vector2f(NAME_POS);
        namePos.scale(scale);
        Vector2f textPos = new Vector2f(TEXT_POS);
        textPos.scale(scale);

        str.draw(namePos);
        str2.draw(textPos);
        str.dispose();
        str2.dispose();
        return height;
    }

    /**
     * Draws messages on the side of the screen (in the "text box").
     */
    public void drawMessages()
    {
        CombatEngineAPI engine = combatPlugin.engine;
        if (!ChatterConfig.chatterBox) return;
        if (engine == null || !engine.isUIShowingHUD() || engine.getCombatUI().isShowingCommandUI())
        {
            return;
        }

        if (!fontLoaded) loadFont();

        openGL11ForText();

        float scale = Global.getSettings().getScreenScaleMult();
        float bw = BOX_WIDTH * scale;

        GL11.glTranslatef(Display.getWidth() - bw - BOX_OFFSET_X * scale + PORTRAIT_WIDTH * scale,
                Display.getHeight() - BOX_OFFSET_Y * scale, 0);

        float remainingHeight = (BOX_HEIGHT - 8 * 2) * scale;
        for (int i=combatPlugin.boxMessages.size() - 1; i>=0; i--)
        {
            ChatterCombatPlugin.BoxMessage msg = combatPlugin.boxMessages.get(i);
            float height = drawMessage(msg, remainingHeight);
            GL11.glTranslatef(0, -height - 4*scale, 0);
            remainingHeight -= height + 4*scale;
            if (remainingHeight < 14 * scale)
                break;
        }
        closeGL11ForText();
    }

    /**
     * Draws the box around the chatter side messages.
     */
    public void drawBox() {
        if (!DRAW_BOX || !ChatterConfig.chatterBox) return;

        float alpha = combatPlugin.engine.getCombatUI().getCommandUIOpacity();
        float scale = Global.getSettings().getScreenScaleMult();

        float bw = BOX_WIDTH * scale;
        float bh = BOX_HEIGHT * scale;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
        GL11.glLineWidth(1);
        GL11.glTranslatef(Display.getWidth() - bw - BOX_OFFSET_X * scale,
                Display.getHeight() - BOX_OFFSET_Y * scale, 0);
        GL11.glColor4f(BOX_COLOR.getRed(), BOX_COLOR.getGreen(), BOX_COLOR.getBlue(), alpha);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(0, 0);
        GL11.glVertex2f(bw, 0);
        GL11.glVertex2f(bw, -bh);
        GL11.glVertex2f(0, -bh);
        GL11.glEnd();

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Draws the box for the fleet intro splash.
     */
    public void drawIntroBox() {
        if (intro == null) return;
        float sizeMult = intro.getSizeMult();
        if (sizeMult <= 0) {
            return;
        }

        if (!fontLoaded || ChatterCombatPlugin.DEBUG_MODE) loadFont();

        float alphaMult = combatPlugin.engine.isUIShowingDialog() ? 0.5f : 1;
        float alpha = SPLASH_ALPHA * alphaMult;
        float offsetX = 0, offsetY = 0;

        if (intro.hasStatic) {
            double rand = Math.random();
            if (rand < 0.2f) alpha -= 0.3f;
            else if (rand > 0.8f) alpha -= 0.1f;
            if (alpha > 1) alpha = 1;
            if (alpha < 0) alpha = 0;

            offsetX = (float)(Math.random() * 1);
            offsetY = (float)(Math.random() * 1);
        }

        // draw text box
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLoadIdentity();
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
        GL11.glLineWidth(1);
        GL11.glTranslatef(Display.getWidth()/2 + offsetX,
                Display.getHeight() * SPLASH_TEXT_YPOS + offsetY, 0);
        GL11.glScalef(sizeMult, 1, 1);
        int halfX = Math.round(Display.getWidth() * SPLASH_TEXT_WIDTH/2 * sizeMult);
        int halfY = Math.round(Display.getHeight() * SPLASH_TEXT_HEIGHT/2);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(1, 1, 1, alpha);
        GL11.glVertex2i(-halfX, halfY);
        GL11.glVertex2i(halfX, halfY);
        GL11.glVertex2i(halfX, -halfY);
        GL11.glVertex2i(-halfX, -halfY);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    public static final int SPOT_MIN_SIZE = 3;
    public static final int SPOT_MAX_SIZE = 12;
    public static final WeightedRandomPicker<Float[]> SPOT_COLOR = new WeightedRandomPicker<>();

    static {
        SPOT_COLOR.add(new Float[] {1f, 0f, 0f});
        SPOT_COLOR.add(new Float[] {0f, 1f, 0f});
        SPOT_COLOR.add(new Float[] {0f, 0f, 1f});
        SPOT_COLOR.add(new Float[] {1f, 1f, 1f});
    }

    public void drawIntroStatic() {
        if (intro == null) return;
        float sizeMult = intro.getSizeMult();
        if (sizeMult <= 0) {
            return;
        }
        if (!intro.hasStatic) return;

        float scale = Global.getSettings().getScreenScaleMult();

        GL11.glPushMatrix();
        // using the SettingsAPI methods instead of the Display ones; unsure why but it's needed to get the static position right with UI scaling
        GL11.glTranslatef(Global.getSettings().getScreenWidth()/2, Global.getSettings().getScreenHeight()/2, 0);
        GL11.glEnable(GL11.GL_BLEND);
        float maxDist = Math.round(Display.getHeight() * SPLASH_IMAGE_HEIGHT / 2)/scale;

        int numSpots = MathUtils.getRandomNumberInRange(64, 256);
        float alpha = intro.getAlphaMult() * 0.5f;
        for (int i=0; i<numSpots; i++) {
            float x = MathUtils.getRandomNumberInRange(-maxDist, maxDist);
            float y = MathUtils.getRandomNumberInRange(-maxDist, maxDist);
            float w = MathUtils.getRandomNumberInRange(SPOT_MIN_SIZE, SPOT_MAX_SIZE)/2/scale;
            float h = MathUtils.getRandomNumberInRange(SPOT_MIN_SIZE, SPOT_MAX_SIZE)/2/scale;

            Float [] color = SPOT_COLOR.pick();
            GL11.glColor4f(color[0], color[1], color[2], alpha);
            GL11.glBegin(GL11.GL_POLYGON);
            GL11.glVertex2f(x-w, y-h);
            GL11.glVertex2f(x+w, y-h);
            GL11.glVertex2f(x+w, y+h);
            GL11.glVertex2f(x-w, y+h);
            GL11.glEnd();
        }
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /**
     * Draws the text and image for the fleet intro splash.
     * @param elapsed Elapsed time passed to the <code>advance()</code> method calling this method.
     */
    public void drawIntro(float elapsed) {
        CombatEngineAPI engine = combatPlugin.engine;
        if (intro == null) {
            return;
        }

        if (!engine.isUIShowingHUD() || engine.getCombatUI().isShowingCommandUI())
        {
            return;
        }

        if (engine.isPaused())
            elapsed = 0;

        float sizeMult = intro.getSizeMult();
        float alphaMult = intro.getAlphaMult() * (engine.isUIShowingDialog() ? 0.5f : 1);
        if (alphaMult < 0) {
            intro = null;
            return;
        }

        if (!fontLoaded || ChatterCombatPlugin.DEBUG_MODE) loadFont();
        float alpha = SPLASH_ALPHA_IMAGE * alphaMult;
        float offsetX = 0, offsetY = 0;

        if (intro.hasStatic) {
            double rand = Math.random();
            if (rand < 0.2f) alpha -= 0.3f;
            else if (rand > 0.8f) alpha -= 0.1f;
            if (alpha > 1) alpha = 1;
            if (alpha < 0) alpha = 0;

            offsetX = (float)(Math.random() * 1);
            offsetY = (float)(Math.random() * 1);
        }

        openGL11ForText();

        // draw logo
        GL11.glPushMatrix();
        GL11.glTranslatef(Display.getWidth()/2 + offsetX, Display.getHeight()/2 + offsetY, 0);
        SpriteAPI sprite = Global.getSettings().getSprite(intro.sprite);
        float spriteSizeMult = Display.getHeight()/sprite.getHeight() * SPLASH_IMAGE_HEIGHT;
        GL11.glScalef(spriteSizeMult, spriteSizeMult, 1);
        sprite.setAlphaMult(alpha);
        sprite.renderAtCenter(0, 0);
        GL11.glPopMatrix();

        // draw text
        alpha = SPLASH_ALPHA * alphaMult;
        if (intro.hasStatic) {
            double rand = Math.random();
            if (rand < 0.2f) alpha -= 0.3f;
            else if (rand > 0.8f) alpha -= 0.1f;
            if (alpha > 1) alpha = 1;
            if (alpha < 0) alpha = 0;
        }

        int baseHeight = Math.round(SPLASH_TEXT_HEIGHT * Display.getHeight()/2);
        if (intro.textHeight == null)
            intro.textHeight = baseHeight;

        int textMaxWidth = Math.round(SPLASH_TEXT_WIDTH * Display.getWidth());

        GL11.glTranslatef(Display.getWidth()/2 + offsetX, Display.getHeight() * SPLASH_TEXT_YPOS + offsetY, 0);

        LazyFont.DrawableString str = fontIntro.createText(StringHelper.getString("chatter_general", "fleetIntroMessage"),
                combatPlugin.getColorWithAlpha(Color.YELLOW, alpha), baseHeight, textMaxWidth);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glScalef(sizeMult, 1, 1);
        GL11.glTranslatef(-str.getWidth()/2, str.getHeight(), 0);
        str.draw(0, 0);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

        // shrink text to fit in the box
        while (true) {
            str = fontIntro.createText(StringHelper.getString("chatter_general", "fleetIntroBracketStart")
                            + intro.name + StringHelper.getString("chatter_general", "fleetIntroBracketEnd"),
                    combatPlugin.getColorWithAlpha(Color.WHITE, alpha), intro.textHeight);
            if (str.getWidth() <= textMaxWidth - 4) break;

            intro.textHeight = Math.round(intro.textHeight * 0.75f);
        }

        GL11.glPushMatrix();
        GL11.glScalef(sizeMult, 1, 1);
        GL11.glTranslatef(-str.getWidth()/2, 0, 0);
        str.draw(0, 0);
        GL11.glPopMatrix();
        closeGL11ForText();

        float elapsedPrev = intro.elapsed;
        intro.elapsed += elapsed;
        if (elapsedPrev < SPLASH_TEXT_TIME_DELAY && intro.elapsed > SPLASH_TEXT_TIME_DELAY) {
            //Global.getLogger(this.getClass()).info("Playing intro sound");
            Global.getSoundPlayer().playUISound(intro.sound, 1, 1);
        }
    }

    public static class FleetIntro
    {
        public static final float TOTAL_TTL = SPLASH_TEXT_TIME_DELAY + SPLASH_TEXT_TIME_IN + SPLASH_TEXT_TIME_PEAK + SPLASH_TEXT_TIME_OUT;

        public String name;
        public String sprite;
        public String sound;
        public float elapsed;
        public Integer textHeight;
        public boolean hasStatic;

        public FleetIntro(String name, String sprite, String sound) {
            this.name = name.toUpperCase();
            this.sprite = sprite;
            this.sound = sound != null ? sound : "chatter_fleet_intro";
        }

        // I should probably have gotten a proper interpolation method but meh
        public float getSizeMult() {
            if (elapsed < SPLASH_TEXT_TIME_DELAY) {
                return 0;
            }
            else if (elapsed < SPLASH_TEXT_TIME_DELAY + SPLASH_TEXT_TIME_IN) {
                float timer = elapsed - SPLASH_TEXT_TIME_DELAY;
                return timer/SPLASH_TEXT_TIME_IN;
            }
            else if (elapsed < SPLASH_TEXT_TIME_DELAY + SPLASH_TEXT_TIME_IN + SPLASH_TEXT_TIME_PEAK) {
                return 1;
            }

            else if (elapsed < TOTAL_TTL) {
                float timer = elapsed - (TOTAL_TTL - SPLASH_TEXT_TIME_OUT);
                return 1 - (timer/SPLASH_TEXT_TIME_OUT);
            }
            else
                return -1;
        }

        public float getAlphaMult() {
            if (elapsed < SPLASH_IMAGE_TIME_IN) {
                return elapsed/SPLASH_IMAGE_TIME_IN;
            }
            else if (elapsed > TOTAL_TTL) {
                return -1;
            }
            else if (elapsed > TOTAL_TTL - SPLASH_IMAGE_TIME_OUT) {
                float timer = elapsed - (TOTAL_TTL - SPLASH_IMAGE_TIME_OUT);
                return 1 - (timer/SPLASH_IMAGE_TIME_OUT);
            }
            else
                return 1;
        }
    }
}
