package universalmod.integration.HwApi;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;


public record HolyWorldLiteApiPayload(String json) implements CustomPayload {
    public static final Identifier CHANNEL_ID = Identifier.of("liteapi", "feature-control");
    public static final Id<HolyWorldLiteApiPayload> TYPE = new Id<>(CHANNEL_ID);

    public static final PacketCodec<RegistryByteBuf, HolyWorldLiteApiPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> buffer.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new HolyWorldLiteApiPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
