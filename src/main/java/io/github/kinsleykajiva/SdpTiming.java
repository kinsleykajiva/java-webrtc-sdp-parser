package io.github.kinsleykajiva;

/**
 * RFC 4566 Timing Field (t=)
 * t=<start-time> <stop-time>
 */
public record SdpTiming(long start, long stop) {
    @Override
    public String toString() {
        return String.format("%d %d", start, stop);
    }
}
