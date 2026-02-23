package io.github.kinsleykajiva;

import java.util.List;
import java.util.Optional;

/**
 * RFC 4566 Media Description (m=)
 * m=<media> <port>/<number of ports> <proto> <fmt> ...
 */
public record SdpMedia(
    String type,
    int port,
    int portCount,
    String protocol,
    List<String> formats,
    Optional<SdpConnection> connection,
    List<SdpBandwidth> bandwidths,
    List<SdpAttribute> attributes
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("m=%s %d%s %s %s\r\n", 
            type, port, (portCount > 1 ? "/" + portCount : ""), protocol, String.join(" ", formats)));
        
        connection.ifPresent(c -> sb.append("c=").append(c).append("\r\n"));
        for (SdpBandwidth b : bandwidths) {
            sb.append("b=").append(b).append("\r\n");
        }
        for (SdpAttribute a : attributes) {
            sb.append(a.toSdpString()).append("\r\n");
        }
        return sb.toString();
    }
}
