package com.maidsoulbag.logic;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.maidsoulbag.MaidSoulBag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broom automation:
 * <ul>
 *   <li>when the owner places a TLM broom (item use -&gt; new EntityBroom), the stored maid with the
 *       lowest favorability is released at the broom position so she can ride along;</li>
 *   <li>the owner is mounted on the broom in the same action, so placing it already puts them in the
 *       saddle instead of having to right-click the broom a second time (TLM's own
 *       {@code EntityBroom#interact} does exactly that, we just skip the extra click);</li>
 *   <li>when the owner dismounts again, the broom is picked up into the first inventory slot (or dropped
 *       when the inventory is full) and that maid is recalled into the bag.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID)
public final class BroomAutomation {
    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_BROOM = new ConcurrentHashMap<>();

    private BroomAutomation() {
    }

    private static final class Link {
        private final UUID playerId;
        private final UUID maidId;
        private boolean ridden;

        private Link(UUID playerId, UUID maidId) {
            this.playerId = playerId;
            this.maidId = maidId;
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof EntityBroom broom)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID ownerId = broom.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null) {
            return;
        }
        // Only one active broom link per player, and only when a bag is worn.
        if (PLAYER_TO_BROOM.containsKey(ownerId) || BagUtil.findBag(player).isEmpty()) {
            return;
        }
        // Do not spawn entities from inside the entity-add event; defer by one tick.
        level.getServer().execute(() -> tryLink(player, broom));
    }

    private static void tryLink(ServerPlayer player, EntityBroom broom) {
        if (broom.isRemoved() || !broom.isAlive()) {
            return;
        }
        UUID ownerId = player.getUUID();
        if (PLAYER_TO_BROOM.containsKey(ownerId) || BagUtil.findBag(player).isEmpty()) {
            return;
        }
        UUID maidId = BagActions.releaseLowestFavorability(player,
                broom.getX(), broom.getY(), broom.getZ(), player.getYRot());
        if (maidId == null) {
            return;
        }
        LINKS.put(broom.getUUID(), new Link(ownerId, maidId));
        PLAYER_TO_BROOM.put(ownerId, broom.getUUID());
        // Place and ride are one action now. TLM only mounts a player who right-clicks the broom,
        // so do the same mount the interact method would have done.
        mountOwner(player, broom);
    }

    /**
     * Mounts the owner on the freshly placed broom.
     * <p>
     * The released maid is left alone on purpose: once the owner is the controlling passenger,
     * TLM's own {@code MaidFollowOwnerVehicleTask} makes a rideable maid hop onto the broom. Mounting
     * the owner first also keeps them in seat 0, which is the seat TLM uses as the pilot
     * ({@code EntityBroom#getControllingPassenger}).
     */
    private static void mountOwner(ServerPlayer player, EntityBroom broom) {
        // Never pull a player off another vehicle, and spectators cannot ride.
        if (player.isPassenger() || player.isSpectator()) {
            return;
        }
        if (broom.isRemoved() || !broom.isAlive()) {
            return;
        }
        if (broom.getPassengers().size() >= 2) {
            return;
        }
        player.startRiding(broom, true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LINKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Link> entry : LINKS.entrySet()) {
            Link link = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(link.playerId);
            if (player == null) {
                // Keep the link across a relog; it is resolved once the player is back.
                continue;
            }
            if (processLink(player, entry.getKey(), link)) {
                remove(entry.getKey(), link.playerId);
            }
        }
    }

    /** @return true when the link is done and should be removed. */
    private static boolean processLink(ServerPlayer player, UUID broomId, Link link) {
        EntityBroom broom = findBroom(player.server, broomId);
        if (broom == null || broom.isRemoved()) {
            finish(player, link, null, false);
            return true;
        }
        if (player.getVehicle() == broom) {
            link.ridden = true;
            return false;
        }
        if (link.ridden) {
            finish(player, link, broom, true);
            return true;
        }
        return false;
    }

    private static void finish(ServerPlayer player, Link link, EntityBroom broom, boolean giveBroom) {
        if (giveBroom && broom != null && !broom.isRemoved()) {
            BagActions.giveBroomItem(player);
            broom.discard();
        }
        boolean recalled = BagActions.recallMaidById(player, link.maidId);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                recalled ? "message.maid_soul_bag.broom_returned"
                        : "message.maid_soul_bag.broom_returned_no_maid"));
    }

    private static void remove(UUID broomId, UUID playerId) {
        LINKS.remove(broomId);
        PLAYER_TO_BROOM.remove(playerId);
    }

    private static EntityBroom findBroom(MinecraftServer server, UUID broomId) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityBroom broom && broom.getUUID().equals(broomId)) {
                    return broom;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Test hooks (used by /maidbag selftest, no effect on normal gameplay)
    // ------------------------------------------------------------------

    /** Simulates the "broom placed" phase for a player, bypassing the player-list lookup. */
    public static boolean debugStart(ServerPlayer player, EntityBroom broom) {
        if (PLAYER_TO_BROOM.containsKey(player.getUUID())) {
            return false;
        }
        UUID maidId = BagActions.releaseLowestFavorability(player,
                broom.getX(), broom.getY(), broom.getZ(), player.getYRot());
        if (maidId == null) {
            return false;
        }
        LINKS.put(broom.getUUID(), new Link(player.getUUID(), maidId));
        PLAYER_TO_BROOM.put(player.getUUID(), broom.getUUID());
        return true;
    }

    /** Forces one automation tick for a specific player. */
    public static void debugTick(ServerPlayer player) {
        UUID broomId = PLAYER_TO_BROOM.get(player.getUUID());
        if (broomId == null) {
            return;
        }
        Link link = LINKS.get(broomId);
        if (link == null) {
            return;
        }
        if (processLink(player, broomId, link)) {
            remove(broomId, player.getUUID());
        }
    }

    /** Marks the link as "was ridden" without actually mounting (test helper). */
    public static void debugMarkRidden(ServerPlayer player) {
        UUID broomId = PLAYER_TO_BROOM.get(player.getUUID());
        Link link = broomId == null ? null : LINKS.get(broomId);
        if (link != null) {
            link.ridden = true;
        }
    }
}
