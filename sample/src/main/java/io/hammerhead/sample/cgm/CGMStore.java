package io.hammerhead.sample.cgm;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, process-wide store for the latest CGM glucose reading.
 *
 * {@link SteloGattCallback} writes here on the BLE callback thread.
 * {@link CGMTransformer} reads here on the Karoo data tick.
 *
 * No explicit locking needed — AtomicReference guarantees visibility.
 */
public class CGMStore {

    private static final CGMStore INSTANCE = new CGMStore();
    private final AtomicReference<GlucosePacket> latest = new AtomicReference<>(null);

    private CGMStore() {}

    public static CGMStore get() {
        return INSTANCE;
    }

    /** Called by SteloGattCallback on a BLE callback thread. */
    public void update(GlucosePacket packet) {
        if (packet != null && packet.isUsable()) {
            latest.set(packet);
        }
    }

    /**
     * Returns the latest usable packet, or {@code null} if none has arrived.
     * Callers should also check {@link GlucosePacket#isStale()}.
     */
    public GlucosePacket getLatest() {
        return latest.get();
    }

    /** Convenience: true only when a fresh, in-range, calibrated reading is available. */
    public boolean hasValidReading() {
        GlucosePacket p = latest.get();
        return p != null && p.isUsable() && !p.isStale();
    }

    /** Called from CGMService.onDestroy() so the Karoo field shows "---" after disconnect. */
    public void clear() {
        latest.set(null);
    }
}