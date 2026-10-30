package universalmod.module.render.richdog;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import universalmod.event.type.AttackEntityEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.module.render.richdog.PetBrain;
import universalmod.module.render.richdog.PetModel;
import universalmod.module.utility.CustomDonate;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.StringSetting;
import universalmod.util.time.TimerUtil;

@ModuleInformation(
        moduleName = "Pesik",
        moduleCategory = ModuleCategory.RENDER
)
public final class RichDog extends Module {
    private static final long TARGET_TIMEOUT_MS = 20_000L;
    private static final Identifier TEX_JACK_RUSSELL = new Identifier("dog", "textures/djekrussel.png");
    private static final Identifier TEX_DACHSHUND = new Identifier("dog", "textures/taksa.png");

    private final PetModel model = new PetModel();
    private final PetBrain brain = new PetBrain();
    private final FlyPetBrain flyBrain = new FlyPetBrain();
    private final ModeSetting skin = new ModeSetting("Skin", "Jack Russell", "Jack Russell", "Dachshund");
    private final BooleanSetting showNameTag = new BooleanSetting("Show Name Tag", true);
    private final StringSetting nameTag = new StringSetting("Name Tag", "Pesik", 512).setVisible(showNameTag::getValue);
    private final ModeSetting intelligence = new ModeSetting("Intelligence", "Default", "Default", "Fly");
    private final BooleanSetting attackPlayers = new BooleanSetting("Attack Player", true);

    private PlayerEntity targetPlayer;
    private final TimerUtil targetTimer = new TimerUtil();

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            flyBrain.reset();
            return;
        }
        var currentTarget = resolveTarget(mc.player);
        if (intelligence.is("Fly")) {
            flyBrain.setOwner(mc.player);
            flyBrain.setTarget(currentTarget);
            flyBrain.tick();
        } else {
            brain.setEntity(mc.player);
            brain.setTarget(currentTarget);
            brain.onUpdate();
            flyBrain.reset();
        }
    }

    @universalmod.event.SubscribeEvent
    private void onAttack(AttackEntityEvent event) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return;
        }
        if (event.target() instanceof PlayerEntity player
                && player != mc.player
                && !player.isSpectator()
                && !isInvisibleWithoutArmor(player)) {
            targetPlayer = player;
            targetTimer.resetCounter();
        }
    }

    @universalmod.event.SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;
        boolean fly = intelligence.is("Fly");
        if (!fly) {
            brain.setEntity(mc.player);
            brain.setTarget(resolveTarget(mc.player));
        }
        Vec3d dogPos = fly ? flyBrain.getPos(event.getPartialTicks()) : brain.getPos();
        if (!isFinite(dogPos)) return;

        MatrixStack matrices = event.getStack();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Vec3d render = dogPos.subtract(camera);
        Identifier texture = skin.is("Dachshund") ? TEX_DACHSHUND : TEX_JACK_RUSSELL;
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texture);
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = consumers.getBuffer(layer);

        matrices.push();
        matrices.translate(render.x, render.y, render.z);
        model.setupAnim(mc.player.age + event.getPartialTicks(), brain, fly ? flyBrain : null);
        model.render(matrices, consumer, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, fly ? flyBrain.getBody() : brain.getBody(),
                !fly && brain.isLay(), fly ? flyBrain : null);
        matrices.pop();
        consumers.draw(layer);
        renderNameTag(matrices, consumers, camera, fly, event.getPartialTicks());
        consumers.draw();
    }

    private void renderNameTag(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Vec3d cameraPos, boolean fly, float delta) {
        if (!showNameTag.getValue() || mc.textRenderer == null) return;
        String raw = nameTag.getValue();
        if (raw == null || raw.isBlank()) return;
        Vec3d dogPos = fly ? flyBrain.getPos(delta) : brain.getPos();
        if (!isFinite(dogPos) || dogPos.squaredDistanceTo(cameraPos) > 4096.0D) return;

        Text component = CustomDonate.parseFormatted(raw);
        float textX = -mc.textRenderer.getWidth(component) / 2.0F;
        int backgroundColor = (int) (mc.options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
        Vec3d render = dogPos.subtract(cameraPos);

        matrices.push();
        matrices.translate(render.x, render.y + 1.35F, render.z);
        matrices.multiply(new org.joml.Quaternionf(mc.getEntityRenderDispatcher().getRotation()));
        matrices.scale(-0.025F, -0.025F, 0.025F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        mc.textRenderer.draw(
                component, textX, 0.0F, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), consumers,
                net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,
                backgroundColor, LightmapTextureManager.MAX_LIGHT_COORDINATE
        );
        consumers.draw();
        mc.textRenderer.draw(
                component, textX, 0.0F, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), consumers,
                net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0, LightmapTextureManager.MAX_LIGHT_COORDINATE
        );
        consumers.draw();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrices.pop();
    }

    private PlayerEntity resolveTarget(PlayerEntity owner) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer != null && targetTimer.isReached(TARGET_TIMEOUT_MS)) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer == null || targetPlayer.isRemoved() || !targetPlayer.isAlive() || targetPlayer.getWorld() != owner.getWorld()) {
            targetPlayer = null;
            return null;
        }
        if (isInvisibleWithoutArmor(targetPlayer)) {
            targetPlayer = null;
            return null;
        }
        return targetPlayer;
    }

    private static boolean isInvisibleWithoutArmor(PlayerEntity player) {
        if (player == null || !player.isInvisible()) return false;
        return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean isFinite(Vec3d p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    @Override
    public void onDisable() {
        targetPlayer = null;
        targetTimer.resetCounter();
        brain.setEntity(null);
        brain.setTarget(null);
        flyBrain.reset();
        super.onDisable();
    }
}
