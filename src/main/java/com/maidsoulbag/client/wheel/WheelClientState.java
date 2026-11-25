package com.maidsoulbag.client.wheel;

/**
 * Small client-only state that must survive closing/reopening the wheel.
 */
public final class WheelClientState {
    /** Last selected row, remembered across openings. */
    public static int lastSelected;

    private WheelClientState() {
    }
}
