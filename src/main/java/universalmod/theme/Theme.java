package universalmod.theme;

import java.awt.Color;

import universalmod.render.animation.Animation;
import universalmod.render.animation.Easing;
import universalmod.render.font.FontType;
import universalmod.render.color.ColorProvider;
import universalmod.render.Render2D;

public class Theme {
    public Animation animation = new Animation(Easing.QUINTIC_OUT, 550L);
    public Animation checkAnimation = new Animation(Easing.QUINTIC_OUT, 350L);
    public float x;
    public float y;
    public String name;
    public int color1;
    public int color2;
    public int colorMain;
    public int colorVisualModules;
    public int colorText;
    public int colorInactiveText;
    public int colorHeaderBg;
    public int colorHeaderText;
    public int colorSlider;
    public int colorSliderCircle;
    public int colorSliderWindow;
    public int colorIndicator;
    public int colorInactiveIndicator;
    public int colorButton;
    public int colorInactiveButton;
    public int colorSeparator;
    public int colorField;
    public int colorInactiveField;
    public int colorTooltipText;
    public int colorWindowBg;
    public int colorIcons;
    public int colorClient;
    private int fromColor1;
    private int fromColor2;

    public Theme(String name, int color1, int color2) {
        this.name = name;
        this.color1 = this.saturateColor(color1);
        this.color2 = this.saturateColor(color2);
        this.fromColor1 = this.color1;
        this.fromColor2 = this.color2;
        this.applyDefaultsFromColors(this.color1, this.color2);
    }

    private void applyDefaultsFromColors(int c1, int c2) {
        this.colorMain = -15921904;
        this.colorVisualModules = c1;
        this.colorText = -1513240;
        this.colorInactiveText = -7829351;
        this.colorHeaderBg = -16119283;
        this.colorHeaderText = c1;
        this.colorSlider = c1;
        this.colorSliderCircle = -1;
        this.colorSliderWindow = -14013899;
        this.colorIndicator = c1;
        this.colorInactiveIndicator = -14013899;
        this.colorButton = c1;
        this.colorInactiveButton = -14013899;
        this.colorSeparator = -14013899;
        this.colorField = -15066590;
        this.colorInactiveField = c2;
        this.colorTooltipText = -1;
        this.colorWindowBg = c2;
        this.colorIcons = c1;
        this.colorClient = c1;
    }

    public void setColors(int newColor1, int newColor2) {
        int sat1 = this.saturateColor(newColor1);
        int sat2 = this.saturateColor(newColor2);
        if (this.color1 != sat1 || this.color2 != sat2) {
            this.startAnimation(this.color1, this.color2);
            this.color1 = sat1;
            this.color2 = sat2;
        }
    }

    public void setAllColors(
            int c1,
            int c2,
            int main,
            int visualModules,
            int text,
            int inactiveText,
            int headerBg,
            int headerText,
            int slider,
            int sliderCircle,
            int sliderWindow,
            int indicator,
            int inactiveIndicator,
            int button,
            int inactiveButton,
            int separator,
            int field,
            int inactiveField,
            int tooltipText,
            int windowBg,
            int icons,
            int client
    ) {
        int sat1 = this.saturateColor(c1);
        int sat2 = this.saturateColor(c2);
        if (this.color1 != sat1 || this.color2 != sat2) {
            this.startAnimation(this.color1, this.color2);
            this.color1 = sat1;
            this.color2 = sat2;
        }

        this.colorMain = main;
        this.colorVisualModules = visualModules;
        this.colorText = text;
        this.colorInactiveText = inactiveText;
        this.colorHeaderBg = headerBg;
        this.colorHeaderText = headerText;
        this.colorSlider = slider;
        this.colorSliderCircle = sliderCircle;
        this.colorSliderWindow = sliderWindow;
        this.colorIndicator = indicator;
        this.colorInactiveIndicator = inactiveIndicator;
        this.colorButton = button;
        this.colorInactiveButton = inactiveButton;
        this.colorSeparator = separator;
        this.colorField = field;
        this.colorInactiveField = inactiveField;
        this.colorTooltipText = tooltipText;
        this.colorWindowBg = windowBg;
        this.colorIcons = icons;
        this.colorClient = client;
    }

    public void startAnimation(int oldColor1, int oldColor2) {
        this.fromColor1 = oldColor1;
        this.fromColor2 = oldColor2;
        this.animation.reset();
    }

    public int getColorFirst() {
        this.animation.run(true);
        float progress = Math.max(0.0F, Math.min(1.0F, this.animation.getValue()));
        return interpolateColorClean(this.fromColor1, this.color1, progress);
    }

    public int getColorSecond() {
        float progress = Math.max(0.0F, Math.min(1.0F, this.animation.getValue()));
        return interpolateColorClean(this.fromColor2, this.color2, progress);
    }

    public int getColorTheme(int index) {
        return getGradientClean(3, index, this.getColorFirst(), this.getColorSecond());
    }

    public int getStaticColorTheme(int index) {
        return getGradientClean(Integer.MAX_VALUE, index, this.getColorFirst(), this.getColorSecond());
    }

    public static int getGradientClean(int speed, int index, int c1, int c2) {
        if (speed != Integer.MAX_VALUE && speed != 0) {
            int time = (int) (System.currentTimeMillis() / (long) Math.max(1, speed) + (long) index);
            float angle = (float) (time % 360) / 360.0F;
            float factor = (float) (-Math.cos((double) angle * Math.PI * 2.0) * 0.5 + 0.5);
            return interpolateColorClean(c1, c2, factor);
        } else {
            return c1;
        }
    }

    public static int interpolateColorClean(int color1, int color2, float amount) {
        amount = Math.min(1.0F, Math.max(0.0F, amount));
        int a1 = color1 >> 24 & 0xFF;
        int r1 = color1 >> 16 & 0xFF;
        int g1 = color1 >> 8 & 0xFF;
        int b1 = color1 & 0xFF;
        int a2 = color2 >> 24 & 0xFF;
        int r2 = color2 >> 16 & 0xFF;
        int g2 = color2 >> 8 & 0xFF;
        int b2 = color2 & 0xFF;
        return (int) ((float) a1 + (float) (a2 - a1) * amount) << 24
                | (int) ((float) r1 + (float) (r2 - r1) * amount) << 16
                | (int) ((float) g1 + (float) (g2 - g1) * amount) << 8
                | (int) ((float) b1 + (float) (b2 - b1) * amount);
    }

    public void drawTheme(double alpha) {
        if (this.name != null && this.name.equals("Радужный")) {
            int[] rainbowColors = new int[4];
            for (int i = 0; i < 4; i++) {
                rainbowColors[i] = Color.HSBtoRGB((float) i / 4.0F, 0.8F, 1.0F);
            }
            Render2D.rect(
                    this.x, this.y, 16.0F, 15.0F, 1.0F,
                    ColorProvider.setAlpha(rainbowColors[0], alpha),
                    ColorProvider.setAlpha(rainbowColors[1], alpha),
                    ColorProvider.setAlpha(rainbowColors[2], alpha),
                    ColorProvider.setAlpha(rainbowColors[3], alpha)
            );
        } else {
            String renderName = this.name != null ? this.name : "Custom";
            Render2D.text(
                    FontType.DEFAULT, renderName, this.x + 2.0F, this.y + 2.25F, 7.0F,
                    ColorProvider.setAlpha(-1, (double) (160.0F + this.checkAnimation.getValue() * 95.0F) * alpha)
            );
            int top = ColorProvider.brighter(ColorProvider.setAlpha(this.color1, alpha * 255.0), 0.5F + this.checkAnimation.getValue() * 0.5F);
            int bottom = ColorProvider.brighter(ColorProvider.setAlpha(this.color2, alpha * 255.0), 0.5F + this.checkAnimation.getValue() * 0.5F);
            Render2D.rect(this.x + 71.5F, this.y, 19.0F, 9.0F, 2.0F, top, top, bottom, bottom);
        }
    }

    private int saturateColor(int color) {
        int alpha = color >> 24 & 0xFF;
        float[] hsb = Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, null);
        hsb[1] = Math.min(1.0F, hsb[1] * 1.5F);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return rgb & 16777215 | alpha << 24;
    }
}
