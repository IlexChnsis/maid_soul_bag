package com.maidsoulbag.network;

import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.logic.WheelService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -&gt; server: perform the wheel action on the selected row.
 */
public record WheelActionPayload(boolean stored, int bagSlot, int entityId) implements CustomPacketPayload {
    public static final Type<WheelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "wheel_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WheelActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WheelActionPayload decode(RegistryFriendlyByteBuf buf) {
                    boolean stored = buf.readBoolean();
                    int bagSlot = buf.readVarInt();
                    int entityId = buf.readVarInt();
                    return new WheelActionPayload(stored, bagSlot, entityId);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, WheelActionPayload payload) {
                    buf.writeBoolean(payload.stored());
                    buf.writeVarInt(payload.bagSlot());
                    buf.writeVarInt(payload.entityId());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WheelActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.stored()) {
                    WheelService.releaseFromSlot(player, payload.bagSlot());
                } else {
                    WheelService.recallEntity(player, payload.entityId());
                }
            }
        });
    }
}
