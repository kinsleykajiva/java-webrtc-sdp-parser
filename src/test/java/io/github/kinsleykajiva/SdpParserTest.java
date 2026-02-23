package io.github.kinsleykajiva;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SdpParserTest {

    @Test
    public void testParseAllExampleSdps() throws IOException {
        Path sdpDir = Paths.get("..", "webrtc-sdp", "examples", "sdps");
        assertTrue(Files.exists(sdpDir), "SDP directory not found: " + sdpDir.toAbsolutePath());

        try (Stream<Path> paths = Files.list(sdpDir)) {
            paths.filter(p -> p.toString().endsWith(".sdp"))
                 .forEach(p -> {
                     try {
                         System.out.println("Testing " + p.getFileName());
                         String sdp = Files.readString(p);
                         SdpSession session = SdpParser.parse(sdp);
                         
                         assertNotNull(session, "Failed to parse session from " + p.getFileName());
                         assertNotNull(session.origin(), "Origin should not be null in " + p.getFileName());
                         assertFalse(session.sessionName().isEmpty(), "Session name should not be empty in " + p.getFileName());
                         
                         // Check if we can reconstruct it (basic check)
                         String reconstructed = session.toString();
                         assertNotNull(reconstructed);
                         assertTrue(reconstructed.startsWith("v=0"), "Reconstructed SDP should start with v=0");
                         
                         System.out.println("Parsed " + session.mediaSections().size() + " media sections");
                     } catch (IOException e) {
                         fail("Failed to read " + p.getFileName() + ": " + e.getMessage());
                     }
                 });
        }
    }
}
