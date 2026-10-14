package media

import (
	"fmt"
	"sync"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
)

type Peer struct {
	ParticipantID string
	MeetingCode   string
	Connection    *webrtc.PeerConnection

	mu                 sync.Mutex
	negotiationMu      sync.Mutex
	renegotiateNeeded  bool
	senders            map[string]*webrtc.RTPSender

	OnICECandidate func(webrtc.ICECandidateInit)
	OnTrack        func(*webrtc.TrackRemote)

	candidateMu       sync.Mutex
	pendingCandidates []webrtc.ICECandidateInit

	OnLocalOffer func(sdp string)
}

func NewPeer(
	participantID string,
	meetingCode string,
) (*Peer, error) {
	api := webrtc.NewAPI()

	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{
				URLs: []string{
					"stun:stun.l.google.com:19302",
				},
			},
		},
	}

	connection, err := api.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("create PeerConnection: %w", err)
	}

	peer := &Peer{
		ParticipantID: participantID,
		MeetingCode:   meetingCode,
		Connection:    connection,
		senders:       make(map[string]*webrtc.RTPSender),
	}

	connection.OnICECandidate(func(candidate *webrtc.ICECandidate) {
		if candidate != nil && peer.OnICECandidate != nil {
			peer.OnICECandidate(candidate.ToJSON())
		}
	})

	connection.OnTrack(func(
		track *webrtc.TrackRemote,
		_ *webrtc.RTPReceiver,
	) {
		if peer.OnTrack != nil {
			peer.OnTrack(track)
		}
	})

	return peer, nil
}

func (peer *Peer) SetRemoteOffer(sdp string) error {
	offer := webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  sdp,
	}

	if err := peer.Connection.SetRemoteDescription(offer); err != nil {
		return fmt.Errorf("set remote offer: %w", err)
	}

	return peer.flushPendingCandidates()
}

func (peer *Peer) SetRemoteAnswer(sdp string) error {
	peer.negotiationMu.Lock()
	defer peer.negotiationMu.Unlock()

	answer := webrtc.SessionDescription{
		Type: webrtc.SDPTypeAnswer,
		SDP:  sdp,
	}

	if err := peer.Connection.SetRemoteDescription(answer); err != nil {
		return fmt.Errorf("set remote answer: %w", err)
	}

	if err := peer.flushPendingCandidates(); err != nil {
		return err
	}

	if peer.renegotiateNeeded {
		peer.renegotiateNeeded = false
		offer, err := peer.Connection.CreateOffer(nil)
		if err == nil {
			if err := peer.Connection.SetLocalDescription(offer); err == nil {
				local := peer.Connection.LocalDescription()
				if local != nil && peer.OnLocalOffer != nil {
					peer.OnLocalOffer(local.SDP)
				}
			}
		}
	}

	return nil
}

func (peer *Peer) AddICECandidate(
	candidate webrtc.ICECandidateInit,
) error {
	peer.candidateMu.Lock()

	if peer.Connection.RemoteDescription() == nil {
		peer.pendingCandidates = append(
			peer.pendingCandidates,
			candidate,
		)
		peer.candidateMu.Unlock()
		return nil
	}

	peer.candidateMu.Unlock()

	if err := peer.Connection.AddICECandidate(candidate); err != nil {
		return fmt.Errorf("add ICE candidate: %w", err)
	}

	return nil
}

func (peer *Peer) flushPendingCandidates() error {
	peer.candidateMu.Lock()
	pending := peer.pendingCandidates
	peer.pendingCandidates = nil
	peer.candidateMu.Unlock()

	for _, candidate := range pending {
		if err := peer.Connection.AddICECandidate(candidate); err != nil {
			return fmt.Errorf("apply pending ICE candidate: %w", err)
		}
	}

	return nil
}

func (peer *Peer) CreateAnswer() (string, error) {
	peer.negotiationMu.Lock()
	defer peer.negotiationMu.Unlock()

	answer, err := peer.Connection.CreateAnswer(nil)
	if err != nil {
		return "", fmt.Errorf("create answer: %w", err)
	}

	if err := peer.Connection.SetLocalDescription(answer); err != nil {
		return "", fmt.Errorf("set local answer: %w", err)
	}

	local := peer.Connection.LocalDescription()
	if local == nil {
		return "", fmt.Errorf("local answer is nil")
	}

	return local.SDP, nil
}

func (peer *Peer) CreateOffer() (string, error) {
	peer.negotiationMu.Lock()
	defer peer.negotiationMu.Unlock()

	offer, err := peer.Connection.CreateOffer(nil)
	if err != nil {
		return "", fmt.Errorf("create offer: %w", err)
	}

	if err := peer.Connection.SetLocalDescription(offer); err != nil {
		return "", fmt.Errorf("set local offer: %w", err)
	}

	local := peer.Connection.LocalDescription()
	if local == nil {
		return "", fmt.Errorf("local offer is nil")
	}

	return local.SDP, nil
}

// Negotiate safely creates and sends a renegotiation offer if the peer is stable,
// or queues the renegotiation if an offer/answer exchange is already in progress.
func (peer *Peer) Negotiate() error {
	peer.negotiationMu.Lock()
	defer peer.negotiationMu.Unlock()

	if peer.Connection.RemoteDescription() == nil {
		return nil
	}

	if peer.Connection.SignalingState() != webrtc.SignalingStateStable {
		peer.renegotiateNeeded = true
		return nil
	}

	peer.renegotiateNeeded = false

	offer, err := peer.Connection.CreateOffer(nil)
	if err != nil {
		return fmt.Errorf("create renegotiation offer: %w", err)
	}

	if err := peer.Connection.SetLocalDescription(offer); err != nil {
		return fmt.Errorf("set local renegotiation offer: %w", err)
	}

	local := peer.Connection.LocalDescription()
	if local != nil && peer.OnLocalOffer != nil {
		peer.OnLocalOffer(local.SDP)
	}

	return nil
}

func (peer *Peer) AddOutgoingTrack(
	trackID string,
	track *webrtc.TrackLocalStaticRTP,
	onRTCPFeedback func([]rtcp.Packet),
) error {
	peer.mu.Lock()
	defer peer.mu.Unlock()

	if _, exists := peer.senders[trackID]; exists {
		return nil
	}

	sender, err := peer.Connection.AddTrack(track)
	if err != nil {
		return fmt.Errorf("add outgoing track: %w", err)
	}

	peer.senders[trackID] = sender

	// Read RTCP from the subscriber and forward feedback (PLI, NACK, etc.)
	// to the publisher so it knows to send keyframes.
	go func() {
		buf := make([]byte, 1500)
		for {
			n, _, err := sender.Read(buf)
			if err != nil {
				return
			}
			if onRTCPFeedback == nil || n == 0 {
				continue
			}
			packets, err := rtcp.Unmarshal(buf[:n])
			if err != nil {
				continue
			}
			onRTCPFeedback(packets)
		}
	}()

	return nil
}

func (peer *Peer) WriteRTCP(packets []rtcp.Packet) error {
	return peer.Connection.WriteRTCP(packets)
}

func (peer *Peer) RemoveOutgoingTrack(trackID string) error {
	peer.mu.Lock()
	sender, exists := peer.senders[trackID]
	if exists {
		delete(peer.senders, trackID)
	}
	peer.mu.Unlock()

	if !exists {
		return nil
	}

	if err := peer.Connection.RemoveTrack(sender); err != nil {
		return fmt.Errorf("remove outgoing track: %w", err)
	}

	return nil
}

func (peer *Peer) Close() error {
	return peer.Connection.Close()
}
