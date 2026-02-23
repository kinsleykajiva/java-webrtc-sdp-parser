package io.github.kinsleykajiva;

/**
 * RFC 4566 Bandwidth Field (b=)
 * b=<bwtype>:<bandwidth>
 */
public record SdpBandwidth(String type, long value) {
    @Override
    public String toString() {
        return String.format("%s:%d", type, value);
    }
}
