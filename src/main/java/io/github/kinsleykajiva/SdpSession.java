package io.github.kinsleykajiva;

import java.util.List;
import java.util.Optional;

/**
 * RFC 4566 Session Description
 */
public record SdpSession(
    int version,
    SdpOrigin origin,
    String sessionName,
    Optional<String> sessionInformation,
    Optional<String> uri,
    List<String> emails,
    List<String> phones,
    Optional<SdpConnection> connection,
    List<SdpBandwidth> bandwidths,
    long startTime,
    long stopTime,
    List<SdpAttribute> sessionAttributes,
    List<SdpMedia> mediaSections
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("v=").append(version).append("\r\n");
        sb.append("o=").append(origin).append("\r\n");
        sb.append("s=").append(sessionName).append("\r\n");
        sessionInformation.ifPresent(i -> sb.append("i=").append(i).append("\r\n"));
        uri.ifPresent(u -> sb.append("u=").append(u).append("\r\n"));
        for (String e : emails) sb.append("e=").append(e).append("\r\n");
        for (String p : phones) sb.append("p=").append(p).append("\r\n");
        connection.ifPresent(c -> sb.append("c=").append(c).append("\r\n"));
        for (SdpBandwidth b : bandwidths) sb.append("b=").append(b).append("\r\n");
        sb.append("t=").append(startTime).append(" ").append(stopTime).append("\r\n");
        for (SdpAttribute a : sessionAttributes) {
            sb.append(a.toSdpString()).append("\r\n");
        }
        for (SdpMedia m : mediaSections) {
            sb.append(m.toString());
        }
        return sb.toString();
    }
}
