package io.github.kinsleykajiva;

import java.util.Optional;

/**
 * RFC 4566 Connection Field (c=)
 * c=<nettype> <addrtype> <connection-address>
 */
public record SdpConnection(
    String netType,
    String addrType,
    String address,
    Optional<Integer> ttl,
    Optional<Integer> amount
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s %s", netType, addrType, address));
        ttl.ifPresent(t -> sb.append("/").append(t));
        amount.ifPresent(a -> sb.append("/").append(a));
        return sb.toString();
    }
}
