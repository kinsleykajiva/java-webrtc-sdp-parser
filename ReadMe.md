# java-webrtc-sdp-parser — Java SDP Parser & Builder

![Java Version](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Maven Central](https://img.shields.io/maven-central/v/io.github.kinsleykajiva/java-webrtc-sdp-parser?color=blue)
![WebRTC](https://img.shields.io/badge/WebRTC-Parser-blue?logo=webrtc&logoColor=white)
![Networking](https://img.shields.io/badge/Networking-Protocol-green)
![License](https://img.shields.io/github/license/kinsleykajiva/java-webrtc-sdp-parser?color=brightgreen)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/kinsleykajiva/java-webrtc-sdp-parser/deploy.yml?branch=master&label=deploy)

> A Java library for parsing, representing, and reconstructing WebRTC Session Description Protocol (SDP) documents.  
> Inspired by Mozilla's [`webrtc-sdp`](https://github.com/mozilla/webrtc-sdp) Rust crate. Test SDP files sourced from [mozilla/webrtc-sdp · examples/sdps](https://github.com/mozilla/webrtc-sdp/tree/master/examples/sdps).

---

## Table of Contents

1. [Installation](#installation)
2. [Quick Start](#quick-start)
3. [Why This Exists](#why-this-exists)
4. [Background — What Is SDP?](#background--what-is-sdp)
5. [RFC 4566 Field Reference](#rfc-4566-field-reference)
6. [Architecture & Class Map](#architecture--class-map)
7. [How Parsing Works](#how-parsing-works)
8. [Attribute Parsing Deep-Dive](#attribute-parsing-deep-dive)
9. [Reconstructing SDP from Objects](#reconstructing-sdp-from-objects)
10. [Usage Examples](#usage-examples)
11. [Supported Attributes](#supported-attributes)
12. [Known Limitations & Edge Cases](#known-limitations--edge-cases)
13. [Running the Batch Validator](#running-the-batch-validator)
14. [Contributing](#contributing)
15. [Inspiration & Credits](#inspiration--credits)
16. [License](#license)

---

## Installation

### Maven
Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.kinsleykajiva</groupId>
    <artifactId>java-webrtc-sdp-parser</artifactId>
    <version>0.3.2</version>
</dependency>
```

### Gradle

**Groovy:**
```groovy
implementation 'io.github.kinsleykajiva:java-webrtc-sdp-parser:0.3.2'
```

**Kotlin:**
```kotlin
implementation("io.github.kinsleykajiva:java-webrtc-sdp-parser:0.3.2")
```

---

## Quick Start

Here is a simple example of how to parse and reconstruct an SDP session:

```java
import io.github.kinsleykajiva.SdpParser;
import io.github.kinsleykajiva.SdpSession;

public class Demo {
    public static void main(String[] args) {
        String rawSdp = """
            v=0
            o=- 4294967296 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 RTP/AVP 0
            a=rtpmap:0 PCMU/8000
            """.stripIndent().replace("\n", "\r\n");

        // 1. Parse the SDP string into an immutable object model
        SdpSession session = SdpParser.parse(rawSdp);

        // 2. Access session information
        System.out.println("Session Name: " + session.sessionName());
        System.out.println("Media count: " + session.mediaSections().size());

        // 3. Reconstruct into a valid SDP string
        String reconstructed = session.toString();
        System.out.println("Reconstructed SDP:\\n" + reconstructed);
    }
}
```

---

## Why This Exists

WebRTC signalling depends entirely on SDP — the text blob exchanged between peers during offer/answer negotiation. Every browser, SIP stack, and media server speaks it. Yet working with SDP in Java is painful:

- The raw text is a flat, line-by-line format with no nesting — hard to navigate programmatically.
- Attributes (`a=`) carry the bulk of the information and each has its own mini-grammar.
- Editing one field means re-serialising the whole document by hand.
- Most Java SDP libraries are either tied to JAIN-SIP (heavyweight) or are incomplete stubs.

This library gives you:

- A clean, immutable Java object model (`record`-based) for the entire SDP document.
- A single-call parser: `SdpSession session = SdpParser.parse(rawSdpString);`
- Faithful round-trip serialisation: `session.toString()` reproduces a valid SDP string.
- Typed attribute records for the most common WebRTC attributes (`rtpmap`, `fmtp`, `ice-ufrag`, `fingerprint`, `ssrc`, `msid`, `mid`, …).
- A fallback `Generic` attribute for anything not yet explicitly supported — so nothing is silently dropped.

---

## Background — What Is SDP?

SDP (Session Description Protocol) is defined in **RFC 4566**. It describes multimedia sessions: what media is being exchanged, on which ports, using which codecs, and with what transport security.

In WebRTC, two peers exchange SDPs as part of the **offer/answer** model (RFC 3264):

```
Peer A                       Signalling Server                    Peer B
  |-- createOffer() -------->|                                       |
  |                          |------------- offer SDP ------------->|
  |                          |<------------ answer SDP -------------|
  |<-- setRemoteDescription--|                                       |
```

An SDP document is a plain-text sequence of lines, each of the form:

```
<type>=<value>
```

where `<type>` is always a **single letter**. Lines are ordered and the order is significant.

### Minimal SDP example

```
v=0
o=- 4294967296 2 IN IP4 127.0.0.1
s=SIP Call
c=IN IP4 198.51.100.7
t=0 0
m=video 56436 RTP/SAVPF 120
a=rtpmap:120 VP8/90000
```

---

## RFC 4566 Field Reference

| Field | Letter | Scope | Description |
|-------|--------|-------|-------------|
| Protocol Version | `v=` | Session | Always `0` |
| Origin | `o=` | Session | Uniquely identifies the session and its creator |
| Session Name | `s=` | Session | Human-readable name; `-` when not meaningful |
| Session Information | `i=` | Session / Media | Free-text description |
| URI | `u=` | Session | Link to more information |
| Email | `e=` | Session | Contact email address |
| Phone | `p=` | Session | Contact phone number |
| Connection | `c=` | Session / Media | Network address for the session |
| Bandwidth | `b=` | Session / Media | Bandwidth modifier (e.g. `AS:512`) |
| Timing | `t=` | Session | Start/stop times (NTP seconds; `0 0` = unbounded) |
| Repeat | `r=` | Session | Repeat timing (rarely used in WebRTC) |
| Attribute | `a=` | Session / Media | Extensible key-value or flag annotations |
| Media | `m=` | Media | Opens a media section |

### The `o=` (Origin) field

```
o=<username> <sess-id> <sess-version> <nettype> <addrtype> <unicast-address>
```

- `username` — `-` when not meaningful (most WebRTC stacks)
- `sess-id` — numerically unique ID, often a large integer or NTP timestamp
- `sess-version` — incremented on each re-offer
- `nettype` — always `IN` (Internet)
- `addrtype` — `IP4` or `IP6`
- `unicast-address` — the originating host; `0.0.0.0` / `::` are common in WebRTC

### The `c=` (Connection) field

```
c=<nettype> <addrtype> <connection-address>[/TTL][/number-of-addresses]
```

For multicast: `c=IN IP4 224.0.0.1/127/3`  
For unicast WebRTC: `c=IN IP4 0.0.0.0`

### The `t=` (Timing) field

```
t=<start-time> <stop-time>
```

Both values are seconds since the NTP epoch (1900-01-01 00:00:00 UTC).  
`t=0 0` means **permanent / unbounded** — the overwhelming norm in WebRTC.

### The `m=` (Media) field

```
m=<media> <port>[/<number-of-ports>] <proto> <fmt-list>
```

- `media` — `audio`, `video`, `application`, `text`
- `port` — RTP port (often `9` as a placeholder in WebRTC, with real address in ICE candidates)
- `proto` — transport: `RTP/AVP`, `RTP/SAVP`, `RTP/SAVPF`, `UDP/TLS/RTP/SAVPF`, `DTLS/SCTP`
- `fmt-list` — payload type numbers (for RTP) or protocol identifiers (for SCTP/data channel)

---

## Architecture & Class Map

```
io.github.kinsleykajiva
│
├── SdpParser          — static parse(String) → SdpSession
│
├── SdpSession         — record: the full session document
│   ├── int version
│   ├── SdpOrigin origin
│   ├── String sessionName
│   ├── Optional<String> sessionInformation
│   ├── Optional<String> uri
│   ├── List<String> emails
│   ├── List<String> phones
│   ├── Optional<SdpConnection> connection
│   ├── List<SdpBandwidth> bandwidths
│   ├── long startTime / stopTime
│   ├── List<SdpAttribute> sessionAttributes
│   └── List<SdpMedia> mediaSections
│
├── SdpOrigin          — record: o= field
├── SdpConnection      — record: c= field (with optional TTL / address count)
├── SdpBandwidth       — record: b= field
├── SdpTiming          — record: t= field
│
├── SdpMedia           — record: one m= block + its c=, b=, a= lines
│
└── SdpAttribute       — interface with typed implementations
    ├── Generic         — fallback for unrecognised attributes
    ├── Rtpmap          — a=rtpmap:<pt> <encoding>/<clock>[/<params>]
    ├── FMTP            — a=fmtp:<pt> <format-parameters>
    ├── Mid             — a=mid:<id>
    ├── Msid            — a=msid:<streamId> [<trackId>]
    ├── Ssrc            — a=ssrc:<ssrc> <attribute>[:<value>]
    ├── IceUfrag        — a=ice-ufrag:<ufrag>
    ├── IcePwd          — a=ice-pwd:<password>
    ├── Fingerprint     — a=fingerprint:<hash-algo> <value>
    └── Setup           — a=setup:<role>
```

All session-level and media-level types are **immutable Java records**. There are no setters.

---

## How Parsing Works

`SdpParser.parse(String sdp)` processes the SDP line by line in a single forward pass.

### Step 1 — Split into lines

```java
String[] lines = sdp.split("\\r?\\n");
```

Both CRLF (`\r\n`, required by RFC 4566) and bare LF (common in practice) are accepted.

### Step 2 — Identify field type

Every non-empty line is validated:

```java
if (line.length() < 3 || line.charAt(1) != '=') continue; // skip malformed
char type  = line.charAt(0);   // single letter
String value = line.substring(2); // everything after "x="
```

### Step 3 — State machine: session level vs. media level

The parser maintains a `currentMediaBuilder`. Before any `m=` line is encountered, all fields belong to the **session level**. The moment a `m=` line is seen:

1. The current media builder (if any) is finalised and added to `mediaSections`.
2. A new `SdpMediaBuilder` is created for the incoming media section.
3. All subsequent `c=`, `b=`, `a=` lines are attached to that builder — not the session.

```
v=0          ← session level
o=…          ← session level
s=…          ← session level
c=…          ← session level connection
t=…          ← session level timing
a=group:…    ← session level attribute
m=audio …    ← opens media section 1; previous builder flushed
a=mid:audio  ← belongs to media section 1
m=video …    ← opens media section 2; media section 1 builder flushed
a=mid:video  ← belongs to media section 2
```

After all lines are consumed, the final `currentMediaBuilder` is flushed.

### Step 4 — Dispatch per field type

```java
switch (type) {
    case 'v' -> version = Integer.parseInt(value);
    case 'o' -> origin  = parseOrigin(value);
    case 's' -> sessionName = value;
    case 'c' -> sessionConnection = Optional.of(parseConnection(value));
    case 'b' -> sessionBandwidths.add(parseBandwidth(value));
    case 't' -> { /* parse start/stop */ }
    case 'a' -> sessionAttributes.add(parseAttribute(value));
    // … etc.
}
```

---

## Attribute Parsing Deep-Dive

The `a=` field is where most of the complexity lives. There are two forms:

```
a=<flag>           — property attribute (boolean presence)
a=<name>:<value>   — value attribute
```

`SdpParser.parseAttribute(String raw)` splits on the first `:` and dispatches:

```java
String[] parts = value.split(":", 2);
String name = parts[0];
String val  = parts.length > 1 ? parts[1] : "";

return switch (name.toLowerCase()) {
    case "rtpmap"      -> parseRtpmap(val);
    case "fmtp"        -> parseFmtp(val);
    case "mid"         -> new SdpAttribute.Mid(val);
    case "msid"        -> parseMsid(val);
    case "ssrc"        -> parseSsrc(val);
    case "ice-ufrag"   -> new SdpAttribute.IceUfrag(val);
    case "ice-pwd"     -> new SdpAttribute.IcePwd(val);
    case "fingerprint" -> parseFingerprint(val);
    case "setup"       -> new SdpAttribute.Setup(val);
    default            -> new SdpAttribute.Generic(name, val);
};
```

Any attribute whose name is not explicitly handled becomes a `Generic(name, value)`. This means **no attribute is ever silently dropped** — the round-trip always preserves everything.

### rtpmap

```
a=rtpmap:<payload-type> <encoding-name>/<clock-rate>[/<encoding-parameters>]
```

```java
// a=rtpmap:111 opus/48000/2
//   pt=111, encoding=opus, clock=48000, params=2

record Rtpmap(int payloadType, String encodingName, int clockRate, String encodingParameters)
```

### fmtp

```
a=fmtp:<payload-type> <format-specific-parameters>
```

```java
// a=fmtp:111 minptime=10;useinbandfec=1
record FMTP(int payloadType, String formatParameters)
```

### ssrc

```
a=ssrc:<ssrc-id> <attribute>[:<value>]
```

Common sub-attributes: `cname`, `msid`, `mslabel`, `label`.

```java
// a=ssrc:1234567890 cname:user@host
record Ssrc(long ssrc, String attribute, String value)
```

### fingerprint

```
a=fingerprint:<hash-algorithm> <fingerprint-value>
```

```java
// a=fingerprint:sha-256 AB:CD:…
record Fingerprint(String hashAlgorithm, String fingerprint)
```

### Attributes handled as Generic (pass-through)

These are preserved verbatim but not given dedicated record types yet:

`candidate`, `rtcp`, `rtcp-mux`, `rtcp-rsize`, `rtcp-fb`, `extmap`,
`sendrecv`, `sendonly`, `recvonly`, `inactive`, `bundle-only`,
`end-of-candidates`, `ice-options`, `ice-lite`, `msid-semantic`,
`group`, `sctpmap`, `sctp-port`, `max-message-size`, `imageattr`,
`rid`, `simulcast`, `ptime`, `maxptime`, `identity`

---

## Reconstructing SDP from Objects

Every record implements `toString()` (session level) or contributes to one via `toSdpString()` (attributes).

```java
SdpSession session = SdpParser.parse(rawSdp);

// Reconstruct — produces RFC 4566 compliant SDP string
String reconstructed = session.toString();
```

The reconstruction order follows RFC 4566 §5:

```
v=
o=
s=
[i=]
[u=]
[e=]
[p=]
[c=]
[b=]*
t=
[a=]*
[m= blocks]*
```

Each `m=` block serialises its own `c=`, `b=`, and `a=` lines in insertion order.

---

## Usage Examples

### Parse and inspect a session

```java
String rawSdp = Files.readString(Path.of("offer.sdp"));
SdpSession session = SdpParser.parse(rawSdp);

System.out.println(session.sessionName());          // "SIP Call"
System.out.println(session.origin().username());    // "-"
System.out.println(session.mediaSections().size()); // 3
```

### Iterate media sections and their codecs

```java
for (SdpMedia media : session.mediaSections()) {
    System.out.println(media.type() + " on port " + media.port());

    media.attributes().stream()
         .filter(a -> a instanceof SdpAttribute.Rtpmap)
         .map(a -> (SdpAttribute.Rtpmap) a)
         .forEach(r -> System.out.printf(
             "  Codec PT=%d  %s/%d%n",
             r.payloadType(), r.encodingName(), r.clockRate()));
}
```

### Find the ICE credentials for a media section

```java
SdpMedia audio = session.mediaSections().get(0);

String ufrag = audio.attributes().stream()
    .filter(a -> a instanceof SdpAttribute.IceUfrag)
    .map(a -> ((SdpAttribute.IceUfrag) a).ufrag())
    .findFirst().orElse("not found");

String pwd = audio.attributes().stream()
    .filter(a -> a instanceof SdpAttribute.IcePwd)
    .map(a -> ((SdpAttribute.IcePwd) a).password())
    .findFirst().orElse("not found");
```

### Get the DTLS fingerprint

```java
session.sessionAttributes().stream()
    .filter(a -> a instanceof SdpAttribute.Fingerprint)
    .map(a -> (SdpAttribute.Fingerprint) a)
    .findFirst()
    .ifPresent(f -> System.out.println(f.hashAlgorithm() + " → " + f.fingerprint()));
```

### Check for BUNDLE groups

```java
session.sessionAttributes().stream()
    .filter(a -> "group".equalsIgnoreCase(a.name()))
    .forEach(a -> System.out.println("Group: " + a.value()));
// Output: Group: BUNDLE audio video
```

### Round-trip verification

```java
String original     = Files.readString(Path.of("offer.sdp"));
SdpSession session  = SdpParser.parse(original);
String reconstructed = session.toString();

// Lines should match (modulo line-ending normalisation)
System.out.println(original.strip().equals(reconstructed.strip()) ? "✔ Round-trip OK" : "✘ Mismatch");
```

---

## Supported Attributes

| Attribute | Record Type | Notes |
|-----------|-------------|-------|
| `rtpmap` | `SdpAttribute.Rtpmap` | PT, codec name, clock rate, optional params |
| `fmtp` | `SdpAttribute.FMTP` | PT + free-form format parameter string |
| `mid` | `SdpAttribute.Mid` | Media stream identifier |
| `msid` | `SdpAttribute.Msid` | Stream ID + optional track ID |
| `ssrc` | `SdpAttribute.Ssrc` | SSRC + sub-attribute + optional value |
| `ice-ufrag` | `SdpAttribute.IceUfrag` | ICE username fragment |
| `ice-pwd` | `SdpAttribute.IcePwd` | ICE password |
| `fingerprint` | `SdpAttribute.Fingerprint` | Hash algorithm + hex fingerprint |
| `setup` | `SdpAttribute.Setup` | DTLS role: `active`, `passive`, `actpass` |
| *(everything else)* | `SdpAttribute.Generic` | Name + value preserved verbatim |

---

## Known Limitations & Edge Cases

These are areas where the current implementation may not handle every valid SDP correctly. Contributions that address any of these are very welcome.

| # | Area | Detail |
|---|------|--------|
| 1 | **Multiple `t=` lines** | RFC 4566 allows more than one timing line. Currently only the last one is retained. |
| 2 | **`r=` (repeat) lines** | Repeat timing is parsed and silently ignored; not stored in the object model. |
| 3 | **`z=` (time zone) lines** | Time zone adjustments are not parsed or stored. |
| 4 | **`k=` (encryption key)** | Obsolete but still valid per RFC; silently ignored. |
| 5 | **Multicast `c=` TTL/count** | Parsed and stored, but not validated against multicast address range. |
| 6 | **`a=candidate` parsing** | Stored as `Generic` — individual fields (foundation, component, priority, addr, typ, raddr) are not typed. |
| 7 | **`a=rtcp-fb` parsing** | Stored as `Generic` — feedback types (`nack`, `ccm fir`, etc.) are not typed. |
| 8 | **`a=extmap` parsing** | Stored as `Generic` — extension URI and direction are not typed. |
| 9 | **`a=rid` / `a=simulcast`** | Stored as `Generic` — simulcast layer semantics are not modelled. |
| 10 | **`a=sctpmap` / `a=sctp-port`** | Data channel SDPs parsed partially; sctp-port stored as `Generic`. |
| 11 | **Large `sess-id`** | Stored as `long` — valid up to `2^63-1`; RFC allows values up to `2^63` (unsigned). |
| 12 | **Error recovery** | Malformed attribute values fall back to `Generic`; malformed `m=` / `o=` / `c=` lines may cause silent null results. |
| 13 | **Strict line ordering** | The parser tolerates out-of-order fields (e.g., `a=` before `t=`) without error; RFC 4566 requires a specific order. |

---

## Running the Batch Validator

`Main.java` processes SDP files `02.sdp` through `40.sdp` from the Mozilla test corpus:

```java
// In Main.java — adjust to your local path:
private static final String SDP_BASE_PATH =
    "C:\\path\\to\\webrtc-sdp\\examples\\sdps";
private static final int SDP_START = 2;
private static final int SDP_END   = 40;
```

Run it from IntelliJ or via:

```bash
javac -d out src/**/*.java
java -cp out io.github.kinsleykajiva.Main
```

Sample output per file:

```
┌─────────────────────────────────────────────────────────────
│  Processing: 08.sdp
└─────────────────────────────────────────────────────────────
  ✔  Parse successful
  ├─ Session Name   (s=): SIP Call
  ├─ Origin         (o=): Mozilla-SIPUA-35.0a1 5184 0 IN IP4 0.0.0.0
  ├─ Timing         (t=): 0 0  — permanent/unbounded session
  ├─ Connection     (c=): IN IP4 224.0.0.1/100/12
  ├─ Session Attrs  (a=): 11 attributes
  │    ice-ufrag            ×1   4a799b2e
  │    ice-pwd              ×1   e4cc12a910f106a0a744719425510e17
  │    ice-lite             ×1   (flag — presence only)
  │    group                ×2
  │        ↳ BUNDLE first second
  │        ↳ BUNDLE third
  └─ Media Sections (m=): 3
       ├─ [1] type=audio     port=9       proto=RTP/SAVPF     fmt=[109, 9, 0, 8, 101]
       │       Attributes (a=): 27 total
       │         rtpmap               ×5
       │             ↳ 109 opus/48000/2
       │             ↳ 9 G722/8000
       │         candidate            ×8
       │         ice-ufrag            ×1   00000000
       ...
```

---

## Contributing

Contributions are open and very welcome — SDP has many edge cases and the Mozilla test corpus alone surfaces dozens of them.

### Good first contributions

- Add a typed record for `a=candidate` (ICE candidate parsing)
- Add a typed record for `a=rtcp-fb` (RTCP feedback types)
- Add a typed record for `a=extmap` (RTP header extension map)
- Add a typed record for `a=simulcast` / `a=rid`
- Handle multiple `t=` lines (store as `List<SdpTiming>`)
- Add `r=` (repeat) and `z=` (timezone) support
- Add strict RFC 4566 line-order validation (opt-in)
- Add a mutable builder API alongside the immutable records

### How to contribute

1. Fork the repository.
2. Create a branch: `git checkout -b feature/candidate-parsing`
3. Add or modify the relevant record in `SdpAttribute.java` and the corresponding `case` in `SdpParser.parseAttribute()`.
4. Add a test SDP (or use one from `examples/sdps`) that exercises your change.
5. Verify the round-trip still passes for all files `02.sdp`–`40.sdp`.
6. Open a pull request with a brief description of what the attribute does and why the change is correct.

There is no shame in a partial implementation — a typed record that handles 90% of real-world values and falls back to `Generic` for the other 10% is far better than nothing.

---

## Inspiration & Credits

This library is directly inspired by Mozilla's [`webrtc-sdp`](https://github.com/mozilla/webrtc-sdp) crate, written in Rust. The overall approach — typed attribute records, a linear state-machine parser, and a faithful round-trip serialiser — mirrors the design philosophy of that crate, re-expressed in idiomatic modern Java (records, sealed interfaces, pattern-matching switches).

The SDP test files (`02.sdp` – `40.sdp`) are sourced from the Mozilla repository's [`examples/sdps`](https://github.com/mozilla/webrtc-sdp/tree/master/examples/sdps) directory and represent a broad range of real-world WebRTC and SIP SDP documents.

**Standards referenced:**

| RFC | Title |
|-----|-------|
| [RFC 4566](https://www.rfc-editor.org/rfc/rfc4566) | SDP: Session Description Protocol |
| [RFC 3264](https://www.rfc-editor.org/rfc/rfc3264) | An Offer/Answer Model with SDP |
| [RFC 5245](https://www.rfc-editor.org/rfc/rfc5245) | ICE: Interactive Connectivity Establishment |
| [RFC 5764](https://www.rfc-editor.org/rfc/rfc5764) | SRTP Extension for DTLS |
| [RFC 5888](https://www.rfc-editor.org/rfc/rfc5888) | SDP Grouping Framework |
| [RFC 7742](https://www.rfc-editor.org/rfc/rfc7742) | WebRTC Video Processing and Codec Requirements |
| [RFC 8829](https://www.rfc-editor.org/rfc/rfc8829) | JavaScript Session Establishment Protocol (JSEP) |

---

---

## License

This project is licensed under the MIT License - see the [LICENSE](file:///c:/Users/Kinsley/IdeaProjects/webrtc-sdp/java-core-lib/LICENSE) file for details.

---

*This library is a work in progress. If you hit a case it doesn't handle, please open an issue or a pull request — every SDP file is a potential new test case.*
