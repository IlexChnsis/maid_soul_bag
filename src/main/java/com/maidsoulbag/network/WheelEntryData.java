package com.maidsoulbag.network;

/**
 * One row of the maid wheel, sent from the server to the client.
 *
 * @param stored      true when the maid is inside a soul spell of the bag
 * @param bagSlot     bag slot index when {@code stored}, otherwise -1
 * @param entityId    entity id when {@code !stored}, otherwise -1
 * @param nameJson    custom name as a JSON component (empty when the maid has no custom name)
 * @param modelId     TLM model id, used for the fallback model name
 * @param favorability TLM favorability
 * @param health      current health (from NBT for stored maids)
 * @param maxHealth   max health (parsed from the attribute NBT for stored maids)
 * @param status      {@link #STATUS_STORED} / {@link #STATUS_RELEASED} / {@link #STATUS_RIDING}
 * @param dimensionId dimension id of a released maid (empty for stored maids)
 */
public record WheelEntryData(
        boolean stored,
        int bagSlot,
        int entityId,
        String nameJson,
        String modelId,
        int favorability,
        float health,
        float maxHealth,
        int status,
        String dimensionId) {

    public static final int STATUS_STORED = 0;
    public static final int STATUS_RELEASED = 1;
    public static final int STATUS_RIDING = 2;
}
