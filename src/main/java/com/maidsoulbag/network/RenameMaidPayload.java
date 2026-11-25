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
 * Client -&gt; server: rename a live maid (inline name field in the maid GUI, no name tag consumed).
 */
public record RenameMaidPayload(int entityId, String name) implements CustomPacketPayload {
    public static final Type<RenameMaidPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "rename_maid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameMaidPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RenameMaidPayload decode(RegistryFriendlyByteBuf buf) {
                    return new RenameMaidPayload(buf.readVarInt(), buf.readUtf());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RenameMaidPayload payload) {
                    buf.writeVarInt(payload.entityId());
                    buf.writeUtf(payload.name());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenameMaidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WheelService.renameMaid(player, payload.entityId(), payload.name());
            }
        });
    }
}
