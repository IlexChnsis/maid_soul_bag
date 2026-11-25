package com.maidsoulbag.network;

import com.maidsoulbag.MaidSoulBag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -&gt; client: the rows shown by the maid wheel.
 */
public record WheelStatePayload(List<WheelEntryData> entries, int reason) implements CustomPacketPayload {
    /** Everything is fine (the list may still be empty when the player has no maids at all). */
    public static final int REASON_OK = 0;
    /** The player does not wear a soul bag. */
    public static final int REASON_NO_BAG = 1;

    public static final Type<WheelStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "wheel_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WheelStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WheelStatePayload decode(RegistryFriendlyByteBuf buf) {
                    int reason = buf.readVarInt();
                    int size = buf.readVarInt();
                    List<WheelEntryData> entries = new ArrayList<>(Math.min(size, 64));
                    for (int i = 0; i < size; i++) {
                        boolean stored = buf.readBoolean();
                        int bagSlot = buf.readVarInt();
                        int entityId = buf.readVarInt();
                        String nameJson = buf.readUtf();
                        String modelId = buf.readUtf();
                        int favorability = buf.readVarInt();
                        float health = buf.readFloat();
                        float maxHealth = buf.readFloat();
                        int status = buf.readVarInt();
                        String dimensionId = buf.readUtf();
                        entries.add(new WheelEntryData(stored, bagSlot, entityId, nameJson, modelId,
                                favorability, health, maxHealth, status, dimensionId));
                    }
                    return new WheelStatePayload(entries, reason);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, WheelStatePayload payload) {
                    buf.writeVarInt(payload.reason());
                    buf.writeVarInt(payload.entries().size());
                    for (WheelEntryData entry : payload.entries()) {
                        buf.writeBoolean(entry.stored());
                        buf.writeVarInt(entry.bagSlot());
                        buf.writeVarInt(entry.entityId());
                        buf.writeUtf(entry.nameJson());
                        buf.writeUtf(entry.modelId());
                        buf.writeVarInt(entry.favorability());
                        buf.writeFloat(entry.health());
                        buf.writeFloat(entry.maxHealth());
                        buf.writeVarInt(entry.status());
                        buf.writeUtf(entry.dimensionId());
                    }
                }
            };

    public WheelStatePayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
