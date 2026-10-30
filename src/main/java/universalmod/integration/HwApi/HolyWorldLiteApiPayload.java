package universalmod.integration.HwApi;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

 
public record HolyWorldLiteApiPayload(String json) {
    public static final Identifier CHANNEL_ID = new Identifier("liteapi", "feature-control");

    public PacketByteBuf encode() {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBytes(json.getBytes(StandardCharsets.UTF_8));
        return out;
    }

    public static HolyWorldLiteApiPayload decode(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new HolyWorldLiteApiPayload(new String(bytes, StandardCharsets.UTF_8));
    }
}
