package com.maidsoulbag.client.wheel;

import com.maidsoulbag.client.MaidNameUtil;
import com.maidsoulbag.network.WheelEntryData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Client side view of one wheel row.
 */
final class WheelRow {
    final Component name;
    final Component favorText;
    final Component healthText;
    final Component statusText;
    final int status;
    final boolean stored;
    final int bagSlot;
    final int entityId;

    private WheelRow(Component name, Component favorText, Component healthText, Component statusText,
                     int status, boolean stored, int bagSlot, int entityId) {
        this.name = name;
        this.favorText = favorText;
        this.healthText = healthText;
        this.statusText = statusText;
        this.status = status;
        this.stored = stored;
        this.bagSlot = bagSlot;
        this.entityId = entityId;
    }

    static WheelRow of(WheelEntryData entry) {
        Component name = parseName(entry);
        Component favor = Component.translatable("gui.maid_soul_bag.favor", entry.favorability());
        Component health = Component.literal(
                Math.max(0, Math.round(entry.health())) + "/" + Math.max(1, Math.round(entry.maxHealth())));
        Component status = statusText(entry);
        return new WheelRow(name, favor, health, status, entry.status(), entry.stored(),
                entry.bagSlot(), entry.entityId());
    }

    private static Component parseName(WheelEntryData entry) {
        if (entry.nameJson() != null && !entry.nameJson().isBlank()) {
            Minecraft minecraft = Minecraft.getInstance();
            try {
                Component parsed = Component.Serializer.fromJson(entry.nameJson(),
                        minecraft.level == null ? net.minecraft.core.RegistryAccess.EMPTY : minecraft.level.registryAccess());
                if (parsed != null && !parsed.getString().isBlank()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // fall back to the model name
            }
        }
        return MaidNameUtil.modelName(entry.modelId());
    }

    private static Component statusText(WheelEntryData entry) {
        String dimension = shortDimension(entry.dimensionId());
        return switch (entry.status()) {
            case WheelEntryData.STATUS_STORED -> Component.translatable("gui.maid_soul_bag.status.stored");
            case WheelEntryData.STATUS_RIDING -> dimension.isEmpty()
                    ? Component.translatable("gui.maid_soul_bag.status.riding")
                    : Component.translatable("gui.maid_soul_bag.status.riding_dim", dimension);
            default -> dimension.isEmpty()
                    ? Component.translatable("gui.maid_soul_bag.status.released")
                    : Component.translatable("gui.maid_soul_bag.status.released_dim", dimension);
        };
    }

    int statusColor() {
        return switch (status) {
            case WheelEntryData.STATUS_STORED -> 0xFF8CC6FF;
            case WheelEntryData.STATUS_RIDING -> 0xFFE8C36A;
            default -> 0xFF7FBF7F;
        };
    }

    private static String shortDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "";
        }
        return switch (dimensionId) {
            case "minecraft:overworld" -> Component.translatable("gui.maid_soul_bag.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("gui.maid_soul_bag.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("gui.maid_soul_bag.dim.end").getString();
            default -> {
                int colon = dimensionId.indexOf(':');
                String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
                yield path.isBlank() ? "" : path;
            }
        };
    }

    /** Name shown in the "cannot recall while riding" hint. */
    Component shortName() {
        return name;
    }

    static float clampSelected(int selected, int size) {
        return Mth.clamp(selected, 0, Math.max(0, size - 1));
    }
}
