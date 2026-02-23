package io.github.kinsleykajiva;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * SDP Parser following RFC 4566
 */
public class SdpParser {

    public static SdpSession parse(String sdp) {
        String[] lines = sdp.split("\\r?\\n");
        
        int version = 0;
        SdpOrigin origin = null;
        String sessionName = "";
        Optional<String> sessionInformation = Optional.empty();
        Optional<String> uri = Optional.empty();
        List<String> emails = new ArrayList<>();
        List<String> phones = new ArrayList<>();
        Optional<SdpConnection> sessionConnection = Optional.empty();
        List<SdpBandwidth> sessionBandwidths = new ArrayList<>();
        long startTime = 0;
        long stopTime = 0;
        List<SdpAttribute> sessionAttributes = new ArrayList<>();
        List<SdpMedia> mediaSections = new ArrayList<>();

        SdpMediaBuilder currentMediaBuilder = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.length() < 3 || line.charAt(1) != '=') {
                continue; // Invalid line
            }

            char type = line.charAt(0);
            String value = line.substring(2);

            if (type == 'm') {
                // New media section starts
                if (currentMediaBuilder != null) {
                    mediaSections.add(currentMediaBuilder.build());
                }
                currentMediaBuilder = parseMediaLine(value);
                continue;
            }

            if (currentMediaBuilder != null) {
                // We are inside a media section
                switch (type) {
                    case 'c' -> currentMediaBuilder.connection = Optional.of(parseConnection(value));
                    case 'b' -> currentMediaBuilder.bandwidths.add(parseBandwidth(value));
                    case 'a' -> currentMediaBuilder.attributes.add(parseAttribute(value));
                    default -> {} // Ignore or handle other types if needed
                }
            } else {
                // We are in the session level
                switch (type) {
                    case 'v' -> version = Integer.parseInt(value);
                    case 'o' -> origin = parseOrigin(value);
                    case 's' -> sessionName = value;
                    case 'i' -> sessionInformation = Optional.of(value);
                    case 'u' -> uri = Optional.of(value);
                    case 'e' -> emails.add(value);
                    case 'p' -> phones.add(value);
                    case 'c' -> sessionConnection = Optional.of(parseConnection(value));
                    case 'b' -> sessionBandwidths.add(parseBandwidth(value));
                    case 't' -> {
                        String[] timing = value.split("\\s+");
                        if (timing.length >= 2) {
                            startTime = Long.parseLong(timing[0]);
                            stopTime = Long.parseLong(timing[1]);
                        }
                    }
                    case 'a' -> sessionAttributes.add(parseAttribute(value));
                    default -> {}
                }
            }
        }

        if (currentMediaBuilder != null) {
            mediaSections.add(currentMediaBuilder.build());
        }

        return new SdpSession(version, origin, sessionName, sessionInformation, uri, emails, phones,
            sessionConnection, sessionBandwidths, startTime, stopTime, sessionAttributes, mediaSections);
    }

    private static SdpOrigin parseOrigin(String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 6) return null;
        return new SdpOrigin(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]), 
            parts[3], parts[4], parts[5]);
    }

    private static SdpConnection parseConnection(String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 3) return null;
        String netType = parts[0];
        String addrType = parts[1];
        String addrPart = parts[2];
        
        Optional<Integer> ttl = Optional.empty();
        Optional<Integer> amount = Optional.empty();
        String address = addrPart;

        if (addrPart.contains("/")) {
            String[] addrTokens = addrPart.split("/");
            address = addrTokens[0];
            if (addrTokens.length >= 2) ttl = Optional.of(Integer.parseInt(addrTokens[1]));
            if (addrTokens.length >= 3) amount = Optional.of(Integer.parseInt(addrTokens[2]));
        }

        return new SdpConnection(netType, addrType, address, ttl, amount);
    }

    private static SdpBandwidth parseBandwidth(String value) {
        String[] parts = value.split(":", 2);
        if (parts.length < 2) return null;
        return new SdpBandwidth(parts[0], Long.parseLong(parts[1]));
    }

    private static SdpAttribute parseAttribute(String value) {
        String[] parts = value.split(":", 2);
        String name = parts[0];
        String val = parts.length > 1 ? parts[1] : "";

        try {
            return switch (name.toLowerCase()) {
                case "rtpmap" -> {
                    String[] vParts = val.split("\\s+", 2);
                    if (vParts.length < 2) yield new SdpAttribute.Generic(name, val);
                    int pt = Integer.parseInt(vParts[0]);
                    String[] eParts = vParts[1].split("/");
                    if (eParts.length < 2) yield new SdpAttribute.Generic(name, val);
                    String encName = eParts[0];
                    int clock = Integer.parseInt(eParts[1]);
                    String params = eParts.length > 2 ? eParts[2] : "";
                    yield new SdpAttribute.Rtpmap(pt, encName, clock, params);
                }
                case "fmtp" -> {
                    String[] vParts = val.split("\\s+", 2);
                    if (vParts.length < 1) yield new SdpAttribute.Generic(name, val);
                    yield new SdpAttribute.FMTP(Integer.parseInt(vParts[0]), vParts.length > 1 ? vParts[1] : "");
                }
                case "mid" -> new SdpAttribute.Mid(val);
                case "msid" -> {
                    String[] vParts = val.split("\\s+");
                    if (vParts.length < 1) yield new SdpAttribute.Generic(name, val);
                    yield new SdpAttribute.Msid(vParts[0], vParts.length > 1 ? vParts[1] : "");
                }
                case "ssrc" -> {
                    String[] vParts = val.split("\\s+", 2);
                    if (vParts.length < 1) yield new SdpAttribute.Generic(name, val);
                    long ssrcId = Long.parseLong(vParts[0]);
                    if (vParts.length > 1) {
                        String[] aParts = vParts[1].split(":", 2);
                        yield new SdpAttribute.Ssrc(ssrcId, aParts[0], aParts.length > 1 ? aParts[1] : "");
                    }
                    yield new SdpAttribute.Ssrc(ssrcId, "", "");
                }
                case "ice-ufrag" -> new SdpAttribute.IceUfrag(val);
                case "ice-pwd" -> new SdpAttribute.IcePwd(val);
                case "fingerprint" -> {
                    String[] vParts = val.split("\\s+", 2);
                    if (vParts.length < 2) yield new SdpAttribute.Generic(name, val);
                    yield new SdpAttribute.Fingerprint(vParts[0], vParts.length > 1 ? vParts[1] : "");
                }
                case "setup" -> new SdpAttribute.Setup(val);
                default -> new SdpAttribute.Generic(name, val);
            };
        } catch (Exception e) {
            return new SdpAttribute.Generic(name, val);
        }
    }

    private static SdpMediaBuilder parseMediaLine(String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 4) return null;

        String type = parts[0];
        String portPart = parts[1];
        int port = 0;
        int portCount = 1;
        if (portPart.contains("/")) {
            String[] pTokens = portPart.split("/");
            port = Integer.parseInt(pTokens[0]);
            portCount = Integer.parseInt(pTokens[1]);
        } else {
            port = Integer.parseInt(portPart);
        }

        String protocol = parts[2];
        List<String> formats = new ArrayList<>(Arrays.asList(parts).subList(3, parts.length));

        SdpMediaBuilder builder = new SdpMediaBuilder();
        builder.type = type;
        builder.port = port;
        builder.portCount = portCount;
        builder.protocol = protocol;
        builder.formats = formats;
        return builder;
    }

    private static class SdpMediaBuilder {
        String type;
        int port;
        int portCount;
        String protocol;
        List<String> formats = new ArrayList<>();
        Optional<SdpConnection> connection = Optional.empty();
        List<SdpBandwidth> bandwidths = new ArrayList<>();
        List<SdpAttribute> attributes = new ArrayList<>();

        SdpMedia build() {
            return new SdpMedia(type, port, portCount, protocol, formats, connection, bandwidths, attributes);
        }
    }
}
