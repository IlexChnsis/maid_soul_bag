package com.maidsoulbag.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Shared helper to resolve the display name of a maid: custom name first, TLM model name as fallback.
 */
public final class MaidNameUtil {
    private MaidNameUtil() {
    }

    public static Component displayName(EntityMaid maid) {
        Component custom = maid.getCustomName();
        if (custom != null && !custom.getString().isBlank()) {
            return custom;
        }
        return modelName(maid.getModelId());
    }

    public static Component modelName(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            Optional<MaidModelInfo> info = CustomPackLoader.MAID_MODELS.getInfo(modelId);
            if (info.isPresent()) {
                return Component.translatable(ParseI18n.getI18nKey(info.get().getName()));
            }
        }
        return Component.translatable("gui.maid_soul_bag.unnamed");
    }
}
