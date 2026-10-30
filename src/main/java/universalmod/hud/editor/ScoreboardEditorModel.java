package universalmod.hud.editor;

import net.minecraft.client.gui.DrawContext;
import universalmod.setting.NumberSetting;
import universalmod.setting.Setting;

import java.util.List;

public interface ScoreboardEditorModel {
    float editorX();

    float editorY();

    float editorWidth();

    float editorHeight();

    String editorBackgroundMode();

    void editorBackgroundMode(String value);

    List<Setting> editorSettings();

    NumberSetting editorScaleSetting();

    float editorScalePercent();

    void editorScalePercent(float percent);

    void renderEditorPopupBackground(DrawContext graphics, float x, float y, float width, float height, float radius);
}
