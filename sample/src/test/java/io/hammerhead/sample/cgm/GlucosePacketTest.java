package io.hammerhead.sample.cgm;

import org.junit.Test;

import static org.junit.Assert.*;

public class GlucosePacketTest {

    /**
     * Build a valid 19-byte 0x4E glucose packet matching the spec:
     *
     *   byte  0     opcode = 0x4E
     *   byte  1     statusRaw
     *   bytes 2–5   clock (uint32 LE)
     *   bytes 6–7   sequence (uint16 LE)
     *   bytes 8–9   padding
     *   bytes 10–11 age in seconds (uint16 LE)
     *   bytes 12–13 glucoseRaw (int16 LE): bits[11:0] = mg/dL, bits[15:12] = flags
     *   byte  14    calibState
     *   byte  15    trendRaw (signed, ×10)
     *   bytes 16–17 predicted (int16 LE, mask & 0x03FF)
     *   bytes 18    padding to reach minimum length
     */
    private byte[] buildPacket(int glucoseMgDl, boolean displayOnly,
                               int ageSeconds, int trendRaw, int predicted) {
        byte[] packet = new byte[19];
        packet[0] = 0x4E;                          // opcode
        packet[1] = 0x00;                          // statusRaw
        // bytes 2–5: clock (arbitrary)
        packet[2] = 0x01; packet[3] = 0x00; packet[4] = 0x00; packet[5] = 0x00;
        // bytes 6–7: sequence
        packet[6] = 0x05; packet[7] = 0x00;
        // bytes 8–9: padding
        packet[8] = 0x00; packet[9] = 0x00;
        // bytes 10–11: age in seconds (LE)
        packet[10] = (byte) (ageSeconds & 0xFF);
        packet[11] = (byte) ((ageSeconds >> 8) & 0xFF);
        // bytes 12–13: glucoseRaw
        int rawGlucose = glucoseMgDl & 0x0FFF;
        if (displayOnly) rawGlucose |= 0x1000; // set a flag bit
        packet[12] = (byte) (rawGlucose & 0xFF);
        packet[13] = (byte) ((rawGlucose >> 8) & 0xFF);
        // byte 14: calibState (0 = normal)
        packet[14] = 0x00;
        // byte 15: trendRaw (signed)
        packet[15] = (byte) trendRaw;
        // bytes 16–17: predicted
        packet[16] = (byte) (predicted & 0xFF);
        packet[17] = (byte) ((predicted >> 8) & 0xFF);
        packet[18] = 0x00;
        return packet;
    }

    @Test
    public void parse_validPacket_returnsCorrectGlucose() {
        byte[] packet = buildPacket(120, false, 0, 20, 125);
        GlucosePacket result = GlucosePacket.parse(packet);

        assertNotNull(result);
        assertEquals(120, result.glucoseMgDl);
        assertFalse(result.displayOnly);
        assertEquals(2.0, result.trend, 0.01);  // trendRaw=20 → 20/10 = 2.0
        assertEquals(125, result.predicted);
    }

    @Test
    public void parse_displayOnlyFlag_setsDisplayOnly() {
        byte[] packet = buildPacket(100, true, 0, 0, 100);
        GlucosePacket result = GlucosePacket.parse(packet);

        assertNotNull(result);
        assertTrue(result.displayOnly);
        assertFalse(result.isUsable()); // displayOnly readings are not usable
    }

    @Test
    public void parse_trendUnknown_returnsNaN() {
        byte[] packet = buildPacket(110, false, 0, 127, 110); // 127 = TREND_UNKNOWN
        GlucosePacket result = GlucosePacket.parse(packet);

        assertNotNull(result);
        assertTrue(Double.isNaN(result.trend));
    }

    @Test
    public void parse_negativeTrend_returnsNegativeValue() {
        byte[] packet = buildPacket(90, false, 0, -15, 85); // -15 → -1.5 mg/dL/min
        GlucosePacket result = GlucosePacket.parse(packet);

        assertNotNull(result);
        assertEquals(-1.5, result.trend, 0.01);
    }

    @Test
    public void parse_wrongOpcode_returnsNull() {
        byte[] packet = buildPacket(100, false, 0, 0, 100);
        packet[0] = 0x01; // wrong opcode
        assertNull(GlucosePacket.parse(packet));
    }

    @Test
    public void parse_tooShort_returnsNull() {
        assertNull(GlucosePacket.parse(new byte[10]));
        assertNull(GlucosePacket.parse(null));
        assertNull(GlucosePacket.parse(new byte[0]));
    }

    @Test
    public void isUsable_inRangeNotDisplayOnly_returnsTrue() {
        byte[] packet = buildPacket(100, false, 0, 0, 100);
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertTrue(result.isUsable());
    }

    @Test
    public void isUsable_glucoseTooLow_returnsFalse() {
        byte[] packet = buildPacket(10, false, 0, 0, 10); // ≤ 13
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertFalse(result.isUsable());
    }

    @Test
    public void isUsable_glucoseTooHigh_returnsFalse() {
        byte[] packet = buildPacket(450, false, 0, 0, 450); // ≥ 401
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertFalse(result.isUsable());
    }

    @Test
    public void isStale_oldReading_returnsTrue() {
        int tenMinutesInSeconds = 10 * 60;
        byte[] packet = buildPacket(100, false, tenMinutesInSeconds + 1, 0, 100);
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertTrue(result.isStale());
    }

    @Test
    public void isStale_freshReading_returnsFalse() {
        byte[] packet = buildPacket(100, false, 30, 0, 100); // 30 seconds old
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertFalse(result.isStale());
    }

    @Test
    public void trendSymbol_flatTrend_returnsArrow() {
        byte[] packet = buildPacket(100, false, 0, 5, 100); // 0.5 mg/dL/min = flat
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertEquals("→", result.trendSymbol());
    }

    @Test
    public void trendSymbol_rapidRise_returnsDoubleUp() {
        byte[] packet = buildPacket(100, false, 0, 35, 100); // 3.5 mg/dL/min
        GlucosePacket result = GlucosePacket.parse(packet);
        assertNotNull(result);
        assertEquals("↑↑", result.trendSymbol());
    }
}
