# Architecture

Leet is a group video-calling app built around a **Selective Forwarding Unit (SFU)**. Each participant uploads their camera and microphone once. The Go server forwards those streams to everyone else in the meeting without decoding or re-encoding them.

- [System overview](#system-overview)
- [Backend](#backend)
- [Media path (SFU)](#media-path-sfu)
- [Joining a call](#joining-a-call)
- [Chat, reactions and raised hands](#chat-reactions-and-raised-hands)
- [Where state lives](#where-state-lives)
- [Client (Kotlin Multiplatform)](#client-kotlin-multiplatform)
- [Design decisions](#design-decisions)

Related docs: [protocol.md](protocol.md) (every event and payload) · [infra.md](infra.md) (ports, Redis, deployment).

## System overview

```mermaid
flowchart LR
    subgraph Clients
        AND["Android app<br/>(Compose + Ktor WebRTC)"]
        IOS["iOS app<br/>(Compose + WebRTC-SDK)"]
        DESK["Desktop app<br/>(Compose + webrtc-java)"]
        WEB["Web test client<br/>(HTML + JS)"]
    end

    subgraph Server["Go backend · :8080"]
        REST["REST API<br/>POST /api/v1/meeting"]
        WS["WebSocket hub<br/>/ws/meetings/:code"]
        SFU["Pion SFU<br/>media forwarding"]
    end

    REDIS[("Redis<br/>meetings + participants")]
    STUN["STUN<br/>stun.l.google.com"]

    Clients -- "HTTP: create meeting" --> REST
    Clients <-- "WebSocket: events, chat,<br/>reactions, signaling" --> WS
    Clients <-- "WebRTC: SRTP audio/video (UDP)" --> SFU

    REST --> REDIS
    WS --> REDIS
    WS <--> SFU
    Clients -. "ICE: public address discovery" .-> STUN
    SFU -.-> STUN
```

Each client uses three channels:

| Channel | Transport | Carries |
| --- | --- | --- |
| REST | HTTP | Creating a meeting |
| WebSocket | TCP | Join/leave, participant list, chat, reactions, raised hands, WebRTC offer/answer/ICE |
| WebRTC | UDP (SRTP/SRTCP) | Audio and video, plus RTCP feedback such as keyframe requests |

## Backend

The backend is a single Go binary (`apps/api/cmd/server`).

```mermaid
flowchart TB
    MAIN["main.go<br/>wires everything, listens on :8080"]
    ROUTES["routes/<br/>Gin router + dev CORS"]
    MH["handler/meetingHandler.go<br/>REST"]
    WSH["handler/websocketHandler.go<br/>join flow + WebRTC signaling"]
    HUB["websockets/hub.go<br/>rooms, broadcast,<br/>chat · reactions · hand raise"]
    CLIENT["websockets/client.go<br/>read/write pumps, ping/pong"]
    SVC["services/meetingService.go<br/>meetings + participants"]
    MEDIA["media/<br/>Manager · Peer · PublishedTrack"]
    RDB["redis/client.go"]
    CFG["packages/config<br/>env vars"]

    MAIN --> ROUTES
    ROUTES --> MH & WSH
    MH --> SVC
    WSH --> SVC & HUB & MEDIA
    HUB --> CLIENT
    SVC --> RDB
    RDB --> CFG
```

| Package | Responsibility |
| --- | --- |
| `routes` | HTTP routes and the CORS middleware used by the web test client |
| `handler` | Turns HTTP requests into service calls. Runs the WebSocket join handshake and routes `webrtc.*` events to the SFU |
| `websockets` | One `Client` per connection with separate read and write goroutines. The `Hub` groups clients by meeting code, broadcasts events, and handles chat, reactions and raised hands |
| `media` | One Pion `PeerConnection` per participant. `Manager` tracks every published track and attaches it to every other peer in the meeting |
| `services` | Meeting and participant storage in Redis |
| `utils` | JSON response envelope and error helpers |

## Media path (SFU)

```mermaid
flowchart LR
    A["Alice<br/>camera + mic"] -- "1 upload" --> S{{"SFU"}}
    B["Bob<br/>camera + mic"] -- "1 upload" --> S
    C["Carol<br/>camera + mic"] -- "1 upload" --> S
    S -- "Bob + Carol" --> A
    S -- "Alice + Carol" --> B
    S -- "Alice + Bob" --> C
```

- **Publishing:** when a client's offer brings in a track, `Manager.PublishTrack` wraps it in a `PublishedTrack` and adds a matching local track to every other peer in the meeting. Each of those peers then renegotiates, so the server sends it a new `webrtc.offer`.
- **Forwarding:** RTP packets are copied from the incoming track to every outgoing track as they arrive. Nothing is decoded.
- **Naming:** each forwarded track and stream id is `"<participantId>:<originalId>"`. Clients split on `:` to find whose video a track is.
- **Keyframes:** a new subscriber needs a keyframe before it can show video. The server sends a PLI (picture loss indication) to the publisher when a track is attached, and forwards PLI/FIR requests from subscribers back to the publisher.
- **Codecs:** the server offers only **VP8** and **Opus**. Since packets are forwarded as they are, every subscriber has to decode whatever the publisher sends, so everyone uses one codec every platform can decode (see [Design decisions](#design-decisions)).

## Joining a call

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as REST API
    participant R as Redis
    participant WS as WebSocket handler
    participant H as Hub
    participant M as SFU (media)
    participant O as Others in meeting

    C->>API: POST /api/v1/meeting
    API->>R: HSET meeting:<id>, SET meeting_code:<code> (24 h TTL)
    API-->>C: { code }

    C->>WS: GET /ws/meetings/<code> (upgrade)
    WS->>R: EXISTS meeting_code:<code>
    C->>WS: meeting.join { name }
    WS->>R: HVALS + HSET meeting:participants:<code>
    WS->>H: register client
    WS-->>C: meeting.joined { own participant_id }
    WS-->>C: meeting.already_exists_participants [...]
    WS-->>C: meeting.hand_raise (for each hand already up)
    H-->>O: meeting.participant_joined

    C->>M: webrtc.offer (camera + mic)
    M-->>C: webrtc.answer (with existing participants' tracks)
    M-->>C: webrtc.offer (any tracks that didn't fit in the answer)
    C->>M: webrtc.answer
    C-->>M: ICE candidates (both directions)
    M-->>O: webrtc.offer (adds the new participant's tracks)
```

When a client disconnects, its read loop ends. The server then removes it from the hub, closes its `PeerConnection` (which removes its tracks from everyone else, triggering renegotiation), deletes it from Redis and broadcasts `meeting.left`.

## Chat, reactions and raised hands

None of these are stored. The hub validates each one, adds the sender's identity from the connection (never from the payload), and broadcasts it.

```mermaid
flowchart LR
    IN["Incoming event"] --> T{type}
    T -- chat.message --> C1["trim, 1–1000 chars"] --> C2["add id, participant_id,<br/>name, sent_at"] --> C3["broadcast to everyone<br/>(sender included)"]
    T -- meeting.reaction --> R1["emoji allow-list"] --> R2["150 ms rate limit"] --> R3["broadcast to everyone<br/>except sender"]
    T -- meeting.hand_raise --> H1["store on connection<br/>(ignore if unchanged)"] --> H2["broadcast to everyone<br/>except sender"]
    T -- "webrtc.*" --> W["SFU signaling"]
    T -- other --> E["error: UNKNOWN_EVENT"]
```

- **Chat** is echoed back to the sender. Clients add a message to the list only when the echo arrives, so a message that failed to send never shows as sent.
- **Reactions and raised hands** appear on the sender's screen immediately, so the server doesn't send them back.
- **Raised hands** are kept in memory on the connection, so the server can tell people who join later whose hands are up. Disconnecting clears the hand.

## Where state lives

| State | Location | Lifetime |
| --- | --- | --- |
| Meeting (id, code, status) | Redis | 24 hours |
| Participant list | Redis | Until the participant leaves (key expires after 24 h) |
| WebSocket connections, rooms | Server memory (`Hub`) | While connected |
| Raised hand | Server memory (`Client`) | While connected |
| Peer connections, published tracks | Server memory (`media.Manager`) | While connected |
| Chat messages | Each client's memory only | Until that client leaves |
| Reactions | Nowhere, broadcast only | ~3 seconds on screen |

Because connections and media live in memory, all participants of a meeting must reach the **same server process**. See [infra.md](infra.md#scaling).

## Client (Kotlin Multiplatform)

One shared module (`apps/frontend/shared`) holds the UI, ViewModels and networking for Android, iOS and desktop. Each platform only supplies its WebRTC engine, video renderer, permissions, audio routing and clipboard entry.

```mermaid
flowchart TB
    subgraph Presentation
        HOME["Home screen<br/>HomeViewModel"]
        MEET["Meet screen<br/>MeetViewModel · MeetState"]
    end

    subgraph Domain
        REPO_I["WebSocketRepository<br/>MeetingRtcRepository<br/>HomeRepository"]
        MODELS["MeetEvents · Participant · RtcEvent"]
    end

    subgraph Data
        WSDS["KtorWsDataSourceImpl<br/>(one WebSocket)"]
        MAP["MeetingEventMapper"]
        SIG["WsMeetingSignaling"]
        RTC["KtorWebRtcDataSource<br/>(PeerConnection + local tracks)"]
        HTTP["HomeDataSourceImpl<br/>(REST)"]
    end

    subgraph Platform["Platform (expect/actual)"]
        P1["WebRTC engine"]
        P2["VideoContent renderer"]
        P3["Permissions · audio session · clipboard"]
    end

    HOME --> REPO_I
    MEET --> REPO_I
    REPO_I --> WSDS & RTC & HTTP
    WSDS --> MAP --> MODELS
    WSDS --> SIG --> RTC
    RTC --> P1
    MEET --> P2 & P3
```

- One WebSocket per meeting carries everything. `MeetingEventMapper` turns frames into `MeetEvents` for the ViewModel, and `WsMeetingSignaling` picks out the `webrtc.*` frames for the RTC repository.
- `MeetViewModel` holds `MeetState` (participants, messages, reactions, panels, video tracks) and handles every `MeetAction` from the UI.
- Dependency injection uses Koin. `AppModules.kt` holds the shared bindings, and each platform adds its own `platformModule`.

| | Android | iOS | Desktop |
| --- | --- | --- | --- |
| WebRTC | `AndroidWebRtc` (stream-webrtc) | `IosWebRtc` (WebRTC-SDK) | `JvmWebRtc` (webrtc-java) |
| Video view | `SurfaceViewRenderer` | `RTCMTLVideoView` | Frames drawn as Compose `ImageBitmap` |
| Default server host | `10.0.2.2` (emulator) | `localhost` | `localhost` |

## Design decisions

**SFU instead of peer-to-peer mesh.** In a mesh, each person uploads their video once per other participant, which phones can't keep up with past three or four people. With an SFU each client uploads once, and the server's bandwidth grows with the size of the meeting instead.

**Forward packets, don't transcode.** Forwarding is cheap and adds almost no delay, but the server can't change codecs, so all clients must share one.

**VP8 and Opus only.** Every platform can encode and decode VP8. With Pion's default codec list, iOS chose H264, and the Android emulator's hardware H264 decoder can't render WebRTC video. Allowing only VP8 removes that kind of mismatch. The cost is that iPhones encode VP8 in software instead of H264 in hardware.

**Chat is not stored.** It's a requirement: messages exist only for the people in the meeting at the time. This also means no database writes on the chat path.

**The server sets the sender's identity.** The hub fills `participant_id` and `name` from the connection's join, so a client can't post as someone else in the same meeting. (There are no user accounts yet; a name is whatever the person typed when joining.)

**Validate and rate-limit at the hub.** Message length, the emoji allow-list and the reaction rate limit are enforced in one place for every client, including the web client.
