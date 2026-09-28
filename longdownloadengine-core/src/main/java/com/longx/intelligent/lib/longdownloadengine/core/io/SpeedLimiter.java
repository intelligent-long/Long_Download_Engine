package com.longx.intelligent.lib.longdownloadengine.core.io;

/**
 * Created by LONG on 2026/9/14 at 上午5:49.
 */
public class SpeedLimiter {
    private volatile long maxBytesPerMs;
    private double storedPermits;
    private long nextFreeTicketNanos;

    public SpeedLimiter(long maxBytesPerMs) {
        this.maxBytesPerMs = maxBytesPerMs;
        this.nextFreeTicketNanos = System.nanoTime();
    }

    public void setMaxBytesPerMs(long maxBytesPerMs) {
        this.maxBytesPerMs = maxBytesPerMs;
    }

    public void acquire(int permits) {
        if (maxBytesPerMs <= 0) return;
        long waitNanos = reserveAndGetWaitLength(permits, System.nanoTime());
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private synchronized long reserveAndGetWaitLength(int permits, long nowNanos) {
        if (maxBytesPerMs <= 0) return 0;
        if (nowNanos > nextFreeTicketNanos) {
            double newPermits = (nowNanos - nextFreeTicketNanos) / 1_000_000.0 * maxBytesPerMs;
            storedPermits = Math.min(maxBytesPerMs, storedPermits + newPermits);
            nextFreeTicketNanos = nowNanos;
        }
        long waitNanos = Math.max(0, nextFreeTicketNanos - nowNanos);
        double storedPermitsToSpend = Math.min(permits, storedPermits);
        double freshPermits = permits - storedPermitsToSpend;
        long waitNanosForFreshPermits = (long) ((freshPermits / maxBytesPerMs) * 1_000_000L);
        nextFreeTicketNanos += waitNanosForFreshPermits;
        storedPermits -= storedPermitsToSpend;
        return waitNanos;
    }
}
