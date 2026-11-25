package com.maidsoulbag.event;

import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.logic.WheelService;
import com.maidsoulbag.network.BagActionPayload;
import com.maidsoulbag.network.RenameMaidPayload;
import com.maidsoulbag.network.WheelActionPayload;
import com.maidsoulbag.network.WheelEntryData;
import com.maidsoulbag.network.WheelRequestPayload;
import com.maidsoulbag.network.WheelStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Common (both sides) events: networking plus dropped bag protection.
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BagActionPayload.TYPE, BagActionPayload.STREAM_CODEC, BagActionPayload::handle);

        // Wheel: list request (client -> server) ...
        registrar.playToServer(WheelRequestPayload.TYPE, WheelRequestPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        List<WheelEntryData> entries = WheelService.buildState(player);
                        context.reply(new WheelStatePayload(entries, WheelService.emptyReason(player)));
                    }
                }));

        // ... and the answer (server -> client). The handler only ever runs on the physical client.
        registrar.playToClient(WheelStatePayload.TYPE, WheelStatePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        com.maidsoulbag.client.wheel.WheelScreen.receive(payload);
                    }
                }));

        registrar.playToServer(WheelActionPayload.TYPE, WheelActionPayload.STREAM_CODEC, WheelActionPayload::handle);
        registrar.playToServer(RenameMaidPayload.TYPE, RenameMaidPayload.STREAM_CODEC, RenameMaidPayload::handle);
    }

    @SubscribeEvent
    public static void registerCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("maidbag")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("selftest")
                        .executes(context -> com.maidsoulbag.debug.SelfTest.run(context.getSource()))));
    }

    /** A dropped bag must never burn, despawn or be destroyed - it holds maids. */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ItemEntity item
                && item.getItem().is(MaidSoulBag.SOUL_BAG.get())) {
            item.setInvulnerable(true);
            item.setGlowingTag(true);
            item.setUnlimitedLifetime();
        }
    }
}
