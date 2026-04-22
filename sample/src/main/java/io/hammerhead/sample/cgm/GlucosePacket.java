package io.hammerhead.sample.cgm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed representation of a single Dexcom Stelo / G7 glucose BLE notification.
 *
 * Packet layout (opcode 0x4E, ≥19 bytes, little-endian):
 *   byte  0     opcode (0x4E)
 *   byte  1     statusRaw
 *   bytes 2–5   clock (uint32) — internal sensor time
 *   bytes 6–7   sequence (uint16)
 *   bytes 8–9   padding
 *   bytes 10–11 age in seconds (uint16) → timestamp = now - age*1000
 *   bytes 12–13 glucoseRaw (int16):
 *                 bits[11:0]  = glucose mg/dL
 *                 bits[15:12] = flags (non-zero → displayOnly / not calibrated)
 *   byte  14    calibState
 *   byte  15    trendRaw (signed int8 × 10; 127 = unknown)
 *   bytes 16–17 predicted glucose (int16, mask & 0x03FF)
 */
public class GlucosePacket {

    /** Blood glucose in mg/dL. */
    public final int glucoseMgDl;

    /**
     * True when bits[15:12] of the raw glucose word are non-zero.
     * Indicates a "display only" reading that has not been fully calibrated —
     * treat as informational, not therapeutic.
     */
    public final boolean displayOnly;

    /**
     * Wall-clock time the reading was valid on the sensor
     * (System.currentTimeMillis() − age × 1000).
     */
    public final long timestamp;

    /**
     * Rate of change in mg/dL per minute.
     * {@link Double#isNaN()} when the sensor reports trend as unknown (raw == 127).
     */
    public final double trend;

    /** Sensor-predicted glucose in mg/dL (bits[9:0] of the 16-bit field). */
    public final int predicted;

    /** Raw calibration state byte from the sensor. */
    public final int calibState;

    /** Packet sequence counter. */
    public final int sequence;

    private GlucosePacket(int glucoseMgDl, boolean displayOnly, long timestamp,
                          double trend, int predicted, int calibState, int sequence) {
        this.glucoseMgDl = glucoseMgDl;
        this.displayOnly = displayOnly;
        this.timestamp   = timestamp;
        this.trend       = trend;
        this.predicted   = predicted;
        this.calibState  = calibState;
        this.sequence    = sequence;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Parse a raw BLE notification byte array.
     *
     * @return a {@link GlucosePacket}, or {@code null} if the data is not a
     *         valid 0x4E glucose packet.
     */
    public static GlucosePacket parse(byte[] packet) {
        if (packet == null || packet.length < DexcomSteloProtocol.GLUCOSE_PACKET_MIN_LENGTH) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);

        if (buf.get() != DexcomSteloProtocol.OPCODE_GLUCOSE) return null;

        /* byte 1  */ int statusRaw  = buf.get() & 0xFF;
        /* bytes 2–5  */ long clock  = readUint32(buf);
        /* bytes 6–7  */ int sequence = readUint16(buf);
        /* bytes 8–9  */ readUint16(buf); // padding — discard
        /* bytes 10–11 */ int age    = readUint16(buf);
        long timestamp = System.currentTimeMillis() - (age * 1000L);

        /* bytes 12–13 */
        int glucoseRaw   = buf.getShort();
        boolean displayOnly = (glucoseRaw & 0xF000) != 0;
        int glucose      = glucoseRaw & 0x0FFF;

        /* byte 14 */ int calibState = buf.get() & 0xFF;
        /* byte 15 */ int trendRaw   = buf.get(); // signed byte
        double trend = (trendRaw == DexcomSteloProtocol.TREND_UNKNOWN)
                ? Double.NaN
                : trendRaw / 10.0;

        /* bytes 16–17 */ int predicted = buf.getShort() & 0x03FF;

        return new GlucosePacket(glucose, displayOnly, timestamp,
                trend, predicted, calibState, sequence);
    }

    /**
     * Construct a GlucosePacket directly from xDrip+ broadcast data.
     * Used by XDripReceiver instead of parsing raw BLE bytes.
     */
    public static GlucosePacket fromXDrip(int glucoseMgDl, double trend, long timestamp) {
        return new GlucosePacket(glucoseMgDl, false, timestamp, trend, 0, 0, 0);
    }

    // -------------------------------------------------------------------------
    // Validity
    // -------------------------------------------------------------------------

    /**
     * A reading is usable for display when it is in physiological range,
     * fully calibrated, and not stale.
     *
     * Range check (glucose > 13 && < 401) follows xDrip+ convention —
     * values outside this range indicate a sensor error or warm-up state.
     */
    public boolean isUsable() {
        return glucoseMgDl > 13 && glucoseMgDl < 401 && !displayOnly;
    }

    /**
     * The Stelo transmits every 5 minutes. Treat as stale after 10 minutes
     * so the Karoo field shows "---" rather than a stale number.
     */
    public boolean isStale() {
        return (System.currentTimeMillis() - timestamp) > 10 * 60 * 1000L;
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /** Unicode trend arrow derived from rate-of-change (mg/dL/min). */
    public String trendSymbol() {
        if (Double.isNaN(trend)) return "?";
        if (trend >  3.0) return "↑↑";
        if (trend >  2.0) return "↑";
        if (trend >  1.0) return "↗";
        if (trend > -1.0) return "→";
        if (trend > -2.0) return "↘";
        if (trend > -3.0) return "↓";
        return "↓↓";
    }

    // -------------------------------------------------------------------------
    // Manual LE readers
    // (ByteBuffer's getInt/getShort work in LE mode but these make unsigned
    //  semantics explicit and match the spec comments exactly)
    // -------------------------------------------------------------------------

    private static long readUint32(ByteBuffer b) {
        return  (b.get() & 0xFFL)
                | ((b.get() & 0xFFL) <<  8)
                | ((b.get() & 0xFFL) << 16)
                | ((b.get() & 0xFFL) << 24);
    }

    private static int readUint16(ByteBuffer b) {
        return (b.get() & 0xFF) | ((b.get() & 0xFF) << 8);
    }
}
