package universalmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import universalmod.event.type.DrawEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.event.type.WorldLyricsRenderEvent;
import universalmod.module.Module;
import universalmod.module.misc.ClickGuiModule;
import universalmod.module.ModuleManager;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.FireGlow;
import universalmod.module.render.Hud;
import universalmod.module.render.Waypoints;
import universalmod.lifecycle.ClientRuntime;
import universalmod.gui.clickgui.ClickGui;
import universalmod.service.media.MusicTracker;
import universalmod.feature.savekt.SaveKtAttackTracker;
import universalmod.render.Render3D;
import universalmod.render.world.ambience.AmbiencePostRenderer;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.render.UiShaders;
import universalmod.render.GuiLayerSequencer;
import universalmod.render.font.msdf.MsdfFontReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import universalmod.render.wetworld.WetWorldRenderer;
import universalmod.render.winter.WinterWorldRenderer;
import universalmod.render.atmosphere.AtmosphereMoodRenderer;
import universalmod.render.atmosphere.AtmosphereRainRenderer;
import universalmod.render.atmosphere.SakuraWorldRenderer;
import universalmod.render.atmosphere.WeatherWorldRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

public final class Engine implements ClientModInitializer {
    private static Engine instance;
    private final ClientRuntime runtime = new ClientRuntime();
    private boolean clickGuiBindDown;
    private Matrix4f atmosphereWorldView;

    public Engine() {
        instance = this;
    }

    public static Engine getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return runtime.getModuleManager();
    }

    @Override
    public void onInitializeClient() {
        CoreShaderRegistrationCallback.EVENT.register(UiShaders::registerShaders);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new MsdfFontReloadListener());
        runtime.initClient();
        SaveKtAttackTracker.init();
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    
                    
                    
                    GuiLayerSequencer.render(context, GuiLayerSequencer.Layer.GAME_EVENTS,
                            () -> ClientRuntime.postEvent(new DrawEvent(context, tickDelta, DrawEvent.Layer.GAME)));
                    GuiLayerSequencer.render(context, GuiLayerSequencer.Layer.HUD,
                            () -> runtime.getModuleManager().renderHud(context, tickDelta));
                    if (!(mc.currentScreen instanceof ClickGui) && ClickGui.shouldRenderOverlay()) {
                        GuiLayerSequencer.render(context, GuiLayerSequencer.Layer.CLICK_GUI_OVERLAY,
                                () -> ClickGui.renderPanels(context,
                                        mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));
                    }
                }
        );
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                    if (context.camera() != null) {
                        Render3D.lastCameraPos = context.camera().getPos();
                        Render3D.lastCameraRotation.set(context.camera().getRotation());
                    }
                    if (context.matrixStack() != null) {
                        atmosphereWorldView = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
                    }
                    if (context.matrixStack() != null)
                        ClientRuntime.postEvent(new WorldRenderEvent(context.matrixStack(), context.tickDelta()));
                    runtime.getModuleManager().renderWorld(context);
                    Render3D.render(context);
                }
        );
        
        
        
        WorldRenderEvents.LAST.register(context -> {
                    if (context.camera() == null || context.projectionMatrix() == null
                            || context.matrixStack() == null) return;
                    
                    
                    
                    ClientRuntime.postEvent(new WorldLyricsRenderEvent(
                            new Matrix4f(context.matrixStack().peek().getPositionMatrix()),
                            new Matrix4f(context.projectionMatrix()),
                            context.camera().getPos(),
                            new org.joml.Quaternionf(context.camera().getRotation()),
                            context.tickDelta()));
                }
        );
        WorldRenderEvents.END.register(context -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.world != null && mc.player != null && context.camera() != null && atmosphereWorldView != null) {
                        Matrix4f worldView = new Matrix4f(atmosphereWorldView);
                        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
                        float delta = context.tickDelta();
                        Atmosphere atmosphere = Atmosphere.getInstance();
                        if (atmosphere != null && atmosphere.isWinterActive())
                            WinterWorldRenderer.render(atmosphere, context.camera(), worldView, projection, delta);
                        else if (atmosphere != null && atmosphere.isMoodActive())
                            AtmosphereMoodRenderer.render(atmosphere, context.camera(), worldView, projection, delta);
                        if (atmosphere != null && (atmosphere.isSakuraActive() || atmosphere.isAutumnActive()))
                            SakuraWorldRenderer.render(atmosphere, context.camera(), worldView, delta);
                        Ambience wetAmbience = Ambience.getInstance();
                        if (wetAmbience != null && wetAmbience.isWetActive() &&
                                (atmosphere == null || !atmosphere.isThunderActive()))
                            WetWorldRenderer.render(context.camera(), worldView, projection, delta);
                        if (atmosphere != null && atmosphere.isRainActive()) {
                            AtmosphereRainRenderer.render(atmosphere, context.camera(), worldView, projection, delta);
                            WeatherWorldRenderer.render(atmosphere, context.camera(), worldView, delta);
                        }
                    }
                    atmosphereWorldView = null;
                    Ambience ambience = Ambience.getInstance();
                    AmbiencePostRenderer.apply(ambience != null && ambience.isEnabled() ? ambience.getSaturationFactor() : 1.0F);
                }
        );
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::clickGuiTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
    }

    private void clickGuiTick(MinecraftClient mc) {
        Module clickGui = runtime.getModuleManager().get("ClickGUI");
        int bind = clickGui == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : clickGui.getKey();
        long window = mc.getWindow().getHandle();
        boolean down = isPressed(window, bind);
        ClickGuiModule.pollOpeningBindRelease();
        if (down && !clickGuiBindDown && !ClickGuiModule.shouldSuppressHeldBind(down)) {
            if (mc.currentScreen instanceof ClickGui gui) gui.close();
            else if (mc.currentScreen == null)
                mc.setScreen(new ClickGui(runtime.getModuleManager()));
        }
        if (!down) ClickGuiModule.shouldSuppressHeldBind(false);
        clickGuiBindDown = down;
    }

    private static boolean isPressed(long window, int bind) {
        if (bind < 0) return false;
        if (bind <= GLFW.GLFW_MOUSE_BUTTON_LAST) return GLFW.glfwGetMouseButton(window, bind) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, bind) == GLFW.GLFW_PRESS;
    }

    public static void shutdown() {
        ClientRuntime m = ClientRuntime.getInstance();
        if (m != null) m.closeServices();
        if (m != null && m.getConfigManager() != null) try {
            m.getConfigManager().saveAll();
        } catch (RuntimeException e) {

        }
        FireGlow fire = FireGlow.getInstance();
        if (fire != null) fire.shutdown();
        AmbiencePostRenderer.clear();
        WinterWorldRenderer.close();
        AtmosphereMoodRenderer.close();
        AtmosphereRainRenderer.reset();
        SakuraWorldRenderer.reset();
        WeatherWorldRenderer.reset();
        WetWorldRenderer.close();
        SkyShaderRenderer.close();
        MusicTracker.getInstance().shutdown();
    }
}
