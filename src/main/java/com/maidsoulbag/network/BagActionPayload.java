package com.maidsoulbag.network;

import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.logic.BagActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BagActionPayload(Action action) implements CustomPacketPayload {
    public static final Type<BagActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BagActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BagActionPayload decode(RegistryFriendlyByteBuf buf) {
                    Action[] values = Action.values();
                    int index = buf.readVarInt();
                    return new BagActionPayload(values[Math.max(0, Math.min(values.length - 1, index))]);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, BagActionPayload payload) {
                    buf.writeVarInt(payload.action().ordinal());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BagActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (payload.action()) {
                    case OPEN -> BagActions.open(player);
                    case RELEASE -> BagActions.release(player);
                    case RECALL -> BagActions.recall(player);
                    case PLACE_BROOM -> com.maidsoulbag.logic.BroomPlacer.placeFromBag(player);
                }
            }
        });
    }

    public enum Action {
        OPEN,
        RELEASE,
        RECALL,
        PLACE_BROOM
    }
}
