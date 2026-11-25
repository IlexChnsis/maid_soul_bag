package com.maidsoulbag.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client side configuration: which modifier (if any) must be held together with the key binding.
 * The keys themselves stay in the vanilla "Controls" screen; this file only controls the chord.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public enum Modifier {
        NONE,
        SHIFT,
        CONTROL,
        ALT
    }

    public static final ModConfigSpec.EnumValue<Modifier> OPEN_MODIFIER;
    public static final ModConfigSpec.EnumValue<Modifier> RELEASE_MODIFIER;
    public static final ModConfigSpec.EnumValue<Modifier> RECALL_MODIFIER;
    public static final ModConfigSpec.EnumValue<Modifier> PLACE_BROOM_MODIFIER;
    public static final ModConfigSpec.EnumValue<Modifier> RADIAL_MODIFIER;

    public static final ModConfigSpec.IntValue NAME_BUTTON_X;
    public static final ModConfigSpec.IntValue NAME_BUTTON_Y;
    public static final ModConfigSpec.BooleanValue WHEEL_SOUND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Optional chord modifiers. The key itself is configured in Options -> Controls -> Maid Soul Bag.",
                "Example: set release = CONTROL and bind the release key to K, then Ctrl+K releases.");
        builder.push("modifiers");
        OPEN_MODIFIER = builder
                .comment("Modifier held for 'Open Soul Bag'")
                .defineEnum("open", Modifier.NONE);
        RELEASE_MODIFIER = builder
                .comment("Modifier held for 'Release All Stored Maids'")
                .defineEnum("release", Modifier.NONE);
        RECALL_MODIFIER = builder
                .comment("Modifier held for 'Recall Nearby Maids'")
                .defineEnum("recall", Modifier.NONE);
        PLACE_BROOM_MODIFIER = builder
                .comment("Modifier held for 'Place Broom From Bag'")
                .defineEnum("place_broom", Modifier.NONE);
        RADIAL_MODIFIER = builder
                .comment("Modifier held for the maid wheel (press and hold the wheel key)")
                .defineEnum("radial", Modifier.NONE);
        builder.pop();

        builder.comment("Pencil (rename) button position inside the maid GUI, next to the sound / skin buttons.")
                .push("maid_gui");
        NAME_BUTTON_X = builder
                .comment("Horizontal offset of the rename button (default 32: between info and sound buttons)")
                .defineInRange("name_button_offset_x", 32, -128, 256);
        NAME_BUTTON_Y = builder
                .comment("Vertical offset of the rename button (same row as the TLM buttons: 14)")
                .defineInRange("name_button_offset_y", 14, -128, 256);
        builder.pop();

        WHEEL_SOUND = builder
                .comment("Play soft UI sounds when opening / scrolling / confirming the maid wheel")
                .define("wheel_sound", true);

        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
