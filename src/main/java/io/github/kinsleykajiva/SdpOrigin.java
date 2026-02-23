package io.github.kinsleykajiva;

/**
 * RFC 4566 Origin Field (o=)
 * o=<username> <sess-id> <sess-version> <nettype> <addrtype> <unicast-address>
 */
public record SdpOrigin(
    String username,
    long sessionId,
    long sessionVersion,
    String netType,
    String addrType,
    String unicastAddress
) {
    @Override
    public String toString() {
        return String.format("%s %d %d %s %s %s", 
            username, sessionId, sessionVersion, netType, addrType, unicastAddress);
    }
}
