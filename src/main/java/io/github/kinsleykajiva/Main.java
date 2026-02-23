package io.github.kinsleykajiva;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    
    private static final String SDP_BASE_PATH = "C:\\Users\\Kinsley\\IdeaProjects\\webrtc-sdp\\webrtc-sdp\\examples\\sdps";
    private static final int SDP_START = 2;
    private static final int SDP_END   = 40;
    
    // -------------------------------------------------------------------------
    // Simple result container
    // -------------------------------------------------------------------------
    record SdpResult(String filename, boolean success, String errorMessage, SdpSession session) {
        static SdpResult ok(String filename, SdpSession session)    { return new SdpResult(filename, true,  null,         session); }
        static SdpResult fail(String filename, String errorMessage) { return new SdpResult(filename, false, errorMessage, null);    }
    }
    
    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        printBanner();
        
        List<SdpResult> results = new ArrayList<>();
        
        for (int i = SDP_START; i <= SDP_END; i++) {
            String filename = String.format("%02d.sdp", i);
            Path   sdpPath  = Paths.get(SDP_BASE_PATH, filename);
            results.add(processFile(filename, sdpPath));
        }
        
        printSummary(results);
    }
    
    // -------------------------------------------------------------------------
    // Process a single SDP file
    // -------------------------------------------------------------------------
    private static SdpResult processFile(String filename, Path sdpPath) {
        printSectionHeader("Processing: " + filename);
        
        if (!Files.exists(sdpPath)) {
            String msg = "File not found: " + sdpPath.toAbsolutePath();
            printWarning(msg);
            return SdpResult.fail(filename, msg);
        }
        
        try {
            String     sdpContent = Files.readString(sdpPath);
            SdpSession session    = SdpParser.parse(sdpContent);
            
            printSessionInfo(session);
            printReconstructed(session);
            
            return SdpResult.ok(filename, session);
            
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            printError("Failed to parse " + filename + " — " + msg);
            return SdpResult.fail(filename, msg);
        }
    }
    
    // -------------------------------------------------------------------------
    // Session-level info with full attribute breakdown
    // -------------------------------------------------------------------------
    private static void printSessionInfo(SdpSession session) {
        System.out.println("  ✔  Parse successful");
        
        // ── Core session fields ──────────────────────────────────────────────
        System.out.printf("  ├─ Session Name   (s=): %s%n", nvl(session.sessionName(), "(none)"));
        System.out.printf("  ├─ Origin         (o=): %s%n", session.origin());
        
        // Timing — human-readable
        System.out.printf("  ├─ Timing         (t=): %s%n", formatTiming(session.startTime(), session.stopTime()));
        
        // Connection
        session.connection().ifPresentOrElse(
                c -> System.out.printf("  ├─ Connection     (c=): %s%n", c),
                () -> System.out.println("  ├─ Connection     (c=): (none at session level)")
        );
        
        // Optional session fields
        session.uri().ifPresent(u ->
                                        System.out.printf("  ├─ URI            (u=): %s%n", u));
        session.sessionInformation().ifPresent(i ->
                                                       System.out.printf("  ├─ Information    (i=): %s%n", i));
        
        if (!session.emails().isEmpty())
            System.out.printf("  ├─ Emails         (e=): %s%n", String.join(", ", session.emails()));
        if (!session.phones().isEmpty())
            System.out.printf("  ├─ Phones         (p=): %s%n", String.join(", ", session.phones()));
        
        // Bandwidth
        if (!session.bandwidths().isEmpty())
            System.out.printf("  ├─ Bandwidth      (b=): %s%n",
                    session.bandwidths().stream().map(SdpBandwidth::toString).collect(Collectors.joining(", ")));
        
        // ── Session-level attributes (a=) ───────────────────────────────────
        List<SdpAttribute> sAttrs = session.sessionAttributes();
        System.out.printf("  ├─ Session Attrs  (a=): %d attribute%s%n",
                sAttrs.size(), sAttrs.size() == 1 ? "" : "s");
        if (!sAttrs.isEmpty()) {
            printAttributeBreakdown(sAttrs, "  │    ");
        }
        
        // ── Media sections (m=) ─────────────────────────────────────────────
        List<SdpMedia> media = session.mediaSections();
        System.out.printf("  └─ Media Sections (m=): %d%n", media.size());
        
        for (int i = 0; i < media.size(); i++) {
            SdpMedia m      = media.get(i);
            boolean  isLast = (i == media.size() - 1);
            String   branch = isLast ? "     └─" : "     ├─";
            String   sub    = isLast ? "        " : "     │  ";
            
            // m= header line
            System.out.printf("%s [%d] type=%-8s  port=%-6d  proto=%-14s  fmt=[%s]%n",
                    branch, i + 1,
                    m.type(), m.port(), m.protocol(),
                    String.join(", ", m.formats()));
            
            // c= inside media
            m.connection().ifPresent(c ->
                                             System.out.printf("%s     Connection (c=): %s%n", sub, c));
            
            // b= inside media
            if (!m.bandwidths().isEmpty())
                System.out.printf("%s     Bandwidth  (b=): %s%n", sub,
                        m.bandwidths().stream().map(SdpBandwidth::toString).collect(Collectors.joining(", ")));
            
            // a= inside media — grouped breakdown
            if (!m.attributes().isEmpty()) {
                System.out.printf("%s     Attributes (a=): %d total%n", sub, m.attributes().size());
                printAttributeBreakdown(m.attributes(), sub + "       ");
            }
        }
        
        System.out.println();
    }
    
    /**
     * Groups attributes by name and prints a structured breakdown.
     *
     * Flag attributes  (no value)  →  shown with "(flag)" label
     * Single-value attrs           →  value shown inline (truncated if >72 chars)
     * Multi-value attrs            →  count on header line, each value on its own ↳ line
     *
     * Example output:
     *   rtpmap               ×5
     *       ↳ 109 opus/48000/2
     *       ↳ 9 G722/8000
     *   ice-ufrag            ×1   00000000
     *   sendonly             ×1   (flag)
     */
    private static void printAttributeBreakdown(List<SdpAttribute> attrs, String indent) {
        // Preserve insertion order, group by attribute name
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (SdpAttribute a : attrs) {
            grouped.computeIfAbsent(a.name(), k -> new ArrayList<>()).add(nvl(a.value(), ""));
        }
        
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            String       name   = entry.getKey();
            List<String> values = entry.getValue();
            int          count  = values.size();
            boolean      flag   = values.stream().allMatch(String::isEmpty);
            
            if (flag) {
                System.out.printf("%s%-22s  ×%-3d  (flag — presence only)%n", indent, name, count);
            } else if (count == 1) {
                String v       = values.get(0);
                String display = v.length() > 72 ? v.substring(0, 69) + "…" : v;
                System.out.printf("%s%-22s  ×%-3d  %s%n", indent, name, count, display);
            } else {
                System.out.printf("%s%-22s  ×%d%n", indent, name, count);
                for (String v : values) {
                    String display = v.length() > 68 ? v.substring(0, 65) + "…" : v;
                    System.out.printf("%s    ↳ %s%n", indent, display);
                }
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Reconstructed SDP block
    // -------------------------------------------------------------------------
    private static void printReconstructed(SdpSession session) {
        System.out.println("  ── Reconstructed SDP ──────────────────────────────────────");
        for (String line : session.toString().split("\\r?\\n")) {
            System.out.println("  │  " + line);
        }
        System.out.println("  ────────────────────────────────────────────────────────────");
        System.out.println();
    }
    
    // -------------------------------------------------------------------------
    // Summary table
    // -------------------------------------------------------------------------
    private static void printSummary(List<SdpResult> results) {
        long passed = results.stream().filter(SdpResult::success).count();
        long failed = results.size() - passed;
        
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total files checked : %-38d║%n", results.size());
        System.out.printf( "║  ✔  Passed           : %-38d║%n", passed);
        System.out.printf( "║  ✘  Failed / Missing : %-38d║%n", failed);
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Parsed files:                                               ║");
        
        for (SdpResult r : results) {
            if (r.success()) {
                SdpSession s          = r.session();
                int        mediaCount = s.mediaSections().size();
                int        sessionAttrCount = s.sessionAttributes().size();
                int        mediaAttrCount   = s.mediaSections().stream()
                                                      .mapToInt(m -> m.attributes().size()).sum();
                System.out.printf("║    ✔  %-12s  media=%-3d  session-attrs=%-5d  media-attrs=%-5d║%n",
                        r.filename(), mediaCount, sessionAttrCount, mediaAttrCount);
            }
        }
        
        if (failed > 0) {
            System.out.println("╠══════════════════════════════════════════════════════════════╣");
            System.out.println("║  Failed / Missing files:                                     ║");
            for (SdpResult r : results) {
                if (!r.success()) {
                    String msg = r.errorMessage() == null ? "unknown error" : r.errorMessage();
                    if (msg.length() > 44) msg = msg.substring(0, 41) + "...";
                    System.out.printf("║    ✘  %-12s  %-44s║%n", r.filename(), msg);
                }
            }
        }
        
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
    
    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------
    
    /**
     * Formats the SDP t= (timing) field into a human-readable description.
     * SDP timestamps are seconds since the NTP epoch (1900-01-01).
     * The special value 0 means "unbounded" per RFC 4566.
     */
    private static String formatTiming(long start, long stop) {
        if (start == 0 && stop == 0) {
            return "0 0  — permanent/unbounded session (both values 0 per RFC 4566)";
        }
        String startStr = start == 0 ? "0 (immediate start)" : start + " (NTP seconds since 1900-01-01)";
        String stopStr  = stop  == 0 ? "0 (no end / unbounded)" : stop + " (NTP seconds since 1900-01-01)";
        return startStr + "  →  " + stopStr;
    }
    
    private static String nvl(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
    
    // -------------------------------------------------------------------------
    // Print helpers
    // -------------------------------------------------------------------------
    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            WebRTC SDP Parser — Batch Validation              ║");
        System.out.printf( "║         Files: %02d.sdp  →  %02d.sdp                             ║%n", SDP_START, SDP_END);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
    
    private static void printSectionHeader(String title) {
        System.out.println("┌─────────────────────────────────────────────────────────────");
        System.out.println("│  " + title);
        System.out.println("└─────────────────────────────────────────────────────────────");
    }
    
    private static void printWarning(String msg) {
        System.out.println("  ⚠  " + msg);
        System.out.println();
    }
    
    private static void printError(String msg) {
        System.out.println("  ✘  " + msg);
        System.out.println();
    }
}