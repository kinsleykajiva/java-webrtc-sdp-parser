package io.github.kinsleykajiva;

/**
 * RFC 4566 Attribute Field (a=)
 * a=<attribute>
 * a=<attribute>:<value>
 */
public interface SdpAttribute {
    String name();
    String value();

    default String toSdpString() {
        String val = value();
        if (val == null || val.isEmpty()) {
            return "a=" + name();
        }
        return "a=" + name() + ":" + val;
    }

    record Generic(String name, String value) implements SdpAttribute {}

    record Rtpmap(int payloadType, String encodingName, int clockRate, String encodingParameters) implements SdpAttribute {
        @Override
        public String name() { return "rtpmap"; }
        @Override
        public String value() {
            return payloadType + " " + encodingName + "/" + clockRate + 
                (encodingParameters != null && !encodingParameters.isEmpty() ? "/" + encodingParameters : "");
        }
    }

    record FMTP(int payloadType, String formatParameters) implements SdpAttribute {
        @Override
        public String name() { return "fmtp"; }
        @Override
        public String value() {
            return payloadType + " " + formatParameters;
        }
    }

    record Mid(String id) implements SdpAttribute {
        @Override
        public String name() { return "mid"; }
        @Override
        public String value() { return id; }
    }

    record Msid(String streamId, String trackId) implements SdpAttribute {
        @Override
        public String name() { return "msid"; }
        @Override
        public String value() {
            return streamId + (trackId != null && !trackId.isEmpty() ? " " + trackId : "");
        }
    }

    record Ssrc(long ssrc, String attribute, String value) implements SdpAttribute {
        @Override
        public String name() { return "ssrc"; }
        @Override
        public String value() {
            return ssrc + " " + attribute + (value != null && !value.isEmpty() ? ":" + value : "");
        }
    }
    
    // Additional common attributes
    record IceUfrag(String ufrag) implements SdpAttribute {
        @Override
        public String name() { return "ice-ufrag"; }
        @Override
        public String value() { return ufrag; }
    }

    record IcePwd(String password) implements SdpAttribute {
        @Override
        public String name() { return "ice-pwd"; }
        @Override
        public String value() { return password; }
    }

    record Fingerprint(String hashAlgorithm, String fingerprint) implements SdpAttribute {
        @Override
        public String name() { return "fingerprint"; }
        @Override
        public String value() { return hashAlgorithm + " " + fingerprint; }
    }

    record Setup(String role) implements SdpAttribute {
        @Override
        public String name() { return "setup"; }
        @Override
        public String value() { return role; }
    }
}
