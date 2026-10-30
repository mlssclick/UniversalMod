package universalmod.gui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.feature.savekt.SaveKtManager;

public final class PvpLeaveConfirmScreen extends Screen {
    private final String command;
    private int centerX;
    private int centerY;

    public PvpLeaveConfirmScreen(String command) {
        super(Text.empty());
        this.command = command;
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Выйти").formatted(Formatting.RED), button -> {
            if (command != null) {
                SaveKtManager.sendFinalCommand(command);
                close();
                return;
            }
            SaveKtManager.disconnect();
        }).dimensions(centerX - 90, centerY + 20, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Отменить").formatted(Formatting.GREEN), button -> close())
                .dimensions(centerX + 10, centerY + 20, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        String message = "Вы уверены, что хотите выйти из сервера во время PvP?";
        int x = centerX - textRenderer.getWidth(message) / 2;
        context.drawText(textRenderer, Text.literal(message), x, centerY - 10, 0xFFFFFFFF, false);
    }

    @Override
    public void close() {
        SaveKtManager.reset();
        super.close();
    }
}
