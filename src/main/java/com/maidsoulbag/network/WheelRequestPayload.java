package com.maidsoulbag.network;

import com.maidsoulbag.MaidSoulBag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server: "give me the current wheel contents".
 */
public record WheelRequestPayload() implements CustomPacketPayload {
    public static final WheelRequestPayload INSTANCE = new WheelRequestPayload();

    public static final Type<WheelRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "wheel_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WheelRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WheelRequestPayload decode(RegistryFriendlyByteBuf buf) {
                    return INSTANCE;
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, WheelRequestPayload payload) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
