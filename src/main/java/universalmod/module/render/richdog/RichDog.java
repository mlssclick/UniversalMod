package universalmod.module.render.richdog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.AttackEntityEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.utility.CustomDonate;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.StringSetting;
import universalmod.render.Render3D;
import universalmod.util.TimerUtil;

public final class RichDog extends Module {
    private static final long TARGET_TIMEOUT_MS = 20_000L;
    private static final Identifier TEX_JACK_RUSSELL =
            Identifier.fromNamespaceAndPath("dog", "textures/djekrussel.png");
    private static final Identifier TEX_DACHSHUND =
            Identifier.fromNamespaceAndPath("dog", "textures/taksa.png");

    private final PetModel model = new PetModel();
    private final PetBrain brain = new PetBrain();
    private final FlyPetBrain flyBrain = new FlyPetBrain();
    private final ModeSetting skin = register(new ModeSetting(
            "Skin", "Dog breed.", "Jack Russell", "Jack Russell", "Dachshund"));
    private final BooleanSetting showNameTag = register(new BooleanSetting(
            "Show Name Tag", "Displays the name tag above the dog.", true));
    private final StringSetting nameTag = createNameTagSetting();
    private final ModeSetting intelligence = register(new ModeSetting(
            "Intelligence", "Dog movement and behaviour.", "Default", "Default", "Fly"));
    private final BooleanSetting attackPlayers = register(new BooleanSetting(
            "Attack Player", "Let the dog attack the player you manually hit.", true));

    private Player targetPlayer;
    private final TimerUtil targetTimer = new TimerUtil();

    public RichDog() {
        super("rich_dog", "Pesik", ModuleCategory.RENDER);
    }

    @Override
    protected void onDisable() {
        targetPlayer = null;
        targetTimer.resetCounter();
        brain.setEntity(null);
        brain.setTarget(null);
        flyBrain.reset();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null) {
            flyBrain.reset();
            return;
        }
        LivingEntity currentTarget = resolveTarget(client.player);
        if (intelligence.is("Fly")) {
            flyBrain.setOwner(client.player);
            flyBrain.setTarget(currentTarget);
            flyBrain.tick();
        } else {
            brain.setEntity(client.player);
            brain.setTarget(currentTarget);
            brain.onUpdate();
            flyBrain.reset();
        }
    }

    @SubscribeEvent
    private void onAttack(AttackEntityEvent event) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return;
        }
        if (event.target() instanceof Player player
                && player != mc.player
                && !player.isSpectator()
                && !isInvisibleWithoutArmor(player)) {
            targetPlayer = player;
            targetTimer.resetCounter();
        }
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        boolean fly = intelligence.is("Fly");
        if (!fly) {
            brain.setEntity(mc.player);
            brain.setTarget(resolveTarget(mc.player));
        }

        PoseStack pose = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 render = (fly ? flyBrain.getPos(event.getPartialTicks()) : brain.getPos()).subtract(cam);

        Identifier texture = skin.is("Dachshund") ? TEX_DACHSHUND : TEX_JACK_RUSSELL;
        RenderType renderType = RenderTypes.entityCutoutNoCull(texture);
        VertexConsumer consumer = provider.getBuffer(renderType);

        pose.pushPose();
        pose.translate(render.x, render.y, render.z);
        model.setupAnim(mc.player.tickCount + event.getPartialTicks(), brain, fly ? flyBrain : null);
        model.render(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                fly ? flyBrain.getBody() : brain.getBody(), !fly && brain.isLay(), fly ? flyBrain : null);
        pose.popPose();
        provider.endBatch(renderType);
        renderVanillaNameTag(pose, provider, cam, fly, event.getPartialTicks());
        provider.endBatch();
    }

    private void renderVanillaNameTag(PoseStack pose, MultiBufferSource.BufferSource provider,
                                      Vec3 cameraPos, boolean fly, float partialTicks) {
        if (!showNameTag.getValue() || mc.font == null) {
            return;
        }
        String text = nameTag.getValue();
        if (text == null || text.isBlank()) {
            return;
        }
        Vec3 dogPos = fly ? flyBrain.getPos(partialTicks) : brain.getPos();
        if (!isFinite(dogPos) || dogPos.distanceToSqr(cameraPos) > 4096.0D) {
            return;
        }

        Component component = CustomDonate.parseFormatted(text);
        Font font = mc.font;
        float textX = -font.width(component) / 2.0F;
        float backgroundOpacity = mc.options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;
        Vec3 render = dogPos.subtract(cameraPos);

        pose.pushPose();
        pose.translate(render.x, render.y + 1.35F, render.z);
        pose.mulPose(Render3D.lastCameraRotation);
        pose.scale(0.025F, -0.025F, 0.025F);

        font.drawInBatch(
                component,
                textX,
                0.0F,
                -1,
                false,
                pose.last().pose(),
                provider,
                Font.DisplayMode.SEE_THROUGH,
                backgroundColor,
                LightTexture.FULL_BRIGHT
        );
        font.drawInBatch(
                component,
                textX,
                0.0F,
                -1,
                false,
                pose.last().pose(),
                provider,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
        pose.popPose();
    }

    private static boolean isFinite(Vec3 position) {
        return position != null
                && Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private StringSetting createNameTagSetting() {
        StringSetting setting = new StringSetting(
                "Name Tag",
                "Displayed name above the dog.",
                "Песик",
                512
        );
        setting.visibleWhen(showNameTag::getValue);
        return register(setting);
    }

    private Player resolveTarget(Player owner) {
        if (!attackPlayers.getValue()) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer != null && targetTimer.isReached(TARGET_TIMEOUT_MS)) {
            targetPlayer = null;
            return null;
        }
        if (targetPlayer == null || targetPlayer.isRemoved() || !targetPlayer.isAlive() || targetPlayer.level() != owner.level()) {
            targetPlayer = null;
            return null;
        }
        if (isInvisibleWithoutArmor(targetPlayer)) {
            targetPlayer = null;
            return null;
        }
        return targetPlayer;
    }

    private boolean isInvisibleWithoutArmor(Player player) {
        if (player == null || !player.isInvisible()) {
            return false;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }
}
