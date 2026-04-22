package io.hammerhead.sample.cgm;

import java.util.UUID;

/**
 * Dexcom Stelo / G7 BLE protocol constants.
 * UUIDs sourced from xDrip+ and confirmed against the packet spec.
 */
public final class DexcomSteloProtocol {

    private DexcomSteloProtocol() {}

    // -------------------------------------------------------------------------
    // Device name prefixes used in scan filter and bonded-device matching
    // -------------------------------------------------------------------------
    public static final String NAME_PREFIX_DXC = "DXC";
    public static final String NAME_PREFIX_DEX = "Dex";

    // -------------------------------------------------------------------------
    // GATT UUIDs
    // -------------------------------------------------------------------------

    /** Primary Dexcom CGM GATT service. */
    public static final UUID CGM_SERVICE_UUID =
            UUID.fromString("F8083532-849E-531C-C594-30F1F86A4EA5");

    /** Live glucose notification characteristic (opcode 0x4E). */
    public static final UUID CONTROL_UUID =
            UUID.fromString("F8083534-849E-531C-C594-30F1F86A4EA5");

    /** Historical (backfill) notification characteristic. */
    public static final UUID BACKFILL_UUID =
            UUID.fromString("F8083536-849E-531C-C594-30F1F86A4EA5");

    /** Advertisement service UUID — used as scan filter for first-time pairing. */
    public static final UUID ADVERTISEMENT_UUID =
            UUID.fromString("0000FEBC-0000-1000-8000-00805F9B34FB");

    /** Standard CCCD descriptor — enables/disables BLE notifications. */
    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    // -------------------------------------------------------------------------
    // Packet constants
    // -------------------------------------------------------------------------

    /** Opcode for a live glucose packet on the Control characteristic. */
    public static final byte OPCODE_GLUCOSE = 0x4E;

    /** Minimum packet length for a valid 0x4E glucose packet. */
    public static final int GLUCOSE_PACKET_MIN_LENGTH = 19;

    /** trendRaw value meaning the trend is not computable. */
    public static final int TREND_UNKNOWN = 127;

    // -------------------------------------------------------------------------
    // Manufacturer ID used as fallback scan filter
    // -------------------------------------------------------------------------
    public static final int MANUFACTURER_ID = 0xD0; // 208

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static boolean isDexcomName(String name) {
        return name != null
                && (name.startsWith(NAME_PREFIX_DXC) || name.startsWith(NAME_PREFIX_DEX));
    }
}
