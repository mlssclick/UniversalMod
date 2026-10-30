package universalmod.integration.HwApi;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;


public record HolyWorldLiteApiPayload(String json) implements CustomPacketPayload {
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("liteapi", "feature-control");
    public static final Type<HolyWorldLiteApiPayload> TYPE = new Type<>(CHANNEL_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, HolyWorldLiteApiPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new HolyWorldLiteApiPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
