package media

import (
	"fmt"
	"log/slog"
	"sync"

	"github.com/pion/rtcp"
	"github.com/pion/webrtc/v4"
)

type Manager struct {
	mu     sync.RWMutex
	peers  map[string]*Peer
	tracks map[string]*PublishedTrack
}

func NewManager() *Manager {
	return &Manager{
		peers:  make(map[string]*Peer),
		tracks: make(map[string]*PublishedTrack),
	}
}

func peerKey(meetingCode, participantID string) string {
	return meetingCode + ":" + participantID
}

func (manager *Manager) AddPeer(peer *Peer) error {
	key := peerKey(peer.MeetingCode, peer.ParticipantID)

	manager.mu.Lock()
	defer manager.mu.Unlock()

	if _, exists := manager.peers[key]; exists {
		return fmt.Errorf("peer already exists: %s", key)
	}

	manager.peers[key] = peer
	return nil
}

func (manager *Manager) GetPeer(
	meetingCode string,
	participantID string,
) (*Peer, bool) {
	manager.mu.RLock()
	defer manager.mu.RUnlock()

	peer, exists := manager.peers[peerKey(meetingCode, participantID)]
	return peer, exists
}

// makePLIForwarder returns an RTCP feedback callback that forwards PLI and FIR
// packets from a subscriber back to the publisher, requesting a new keyframe.
func makePLIForwarder(publisher *Peer, remoteSSRC uint32) func([]rtcp.Packet) {
	return func(packets []rtcp.Packet) {
		for _, pkt := range packets {
			switch pkt.(type) {
			case *rtcp.PictureLossIndication, *rtcp.FullIntraRequest:
				_ = publisher.WriteRTCP([]rtcp.Packet{
					&rtcp.PictureLossIndication{
						MediaSSRC: remoteSSRC,
					},
				})
			}
		}
	}
}

// sendPLI sends an immediate PLI to the publisher requesting a keyframe.
// Call this when a new subscriber is attached mid-stream.
func sendPLI(publisher *Peer, remoteSSRC uint32) {
	_ = publisher.WriteRTCP([]rtcp.Packet{
		&rtcp.PictureLossIndication{
			MediaSSRC: remoteSSRC,
		},
	})
}

func (manager *Manager) AttachExistingTracks(peer *Peer) error {
	manager.mu.RLock()
	tracks := make([]*PublishedTrack, 0)

	for _, track := range manager.tracks {
		if track.MeetingCode == peer.MeetingCode &&
			track.OwnerParticipantID != peer.ParticipantID {
			tracks = append(tracks, track)
		}
	}
	manager.mu.RUnlock()

	for _, track := range tracks {
		publisher, exists := manager.GetPeer(track.MeetingCode, track.OwnerParticipantID)
		if !exists {
			publisher = nil
		}

		var cb func([]rtcp.Packet)
		if publisher != nil {
			cb = makePLIForwarder(publisher, uint32(track.Remote.SSRC()))
		}

		if err := peer.AddOutgoingTrack(track.ID, track.Local, cb); err != nil {
			return err
		}

		// Request a keyframe immediately so the new subscriber can start decoding.
		if publisher != nil && track.Remote.Kind() == webrtc.RTPCodecTypeVideo {
			sendPLI(publisher, uint32(track.Remote.SSRC()))
		}
	}

	return nil
}

func (manager *Manager) PublishTrack(
	publisher *Peer,
	remote *webrtc.TrackRemote,
) error {
	track, err := NewPublishedTrack(
		publisher.MeetingCode,
		publisher.ParticipantID,
		remote,
	)
	if err != nil {
		return err
	}

	manager.mu.Lock()

	if _, exists := manager.tracks[track.ID]; exists {
		manager.mu.Unlock()
		track.Stop()
		return fmt.Errorf("published track already exists: %s", track.ID)
	}

	manager.tracks[track.ID] = track

	subscribers := make([]*Peer, 0)
	for _, peer := range manager.peers {
		if peer.MeetingCode == publisher.MeetingCode &&
			peer.ParticipantID != publisher.ParticipantID {
			subscribers = append(subscribers, peer)
		}
	}

	manager.mu.Unlock()

	// Add the published track to every other participant.
	for _, subscriber := range subscribers {
		cb := makePLIForwarder(publisher, uint32(remote.SSRC()))
		if err := subscriber.AddOutgoingTrack(track.ID, track.Local, cb); err != nil {
			_ = manager.RemovePublishedTrack(track.ID)
			return err
		}

		if err := subscriber.Negotiate(); err != nil {
			slog.Error("failed to renegotiate for subscriber",
				"subscriberId", subscriber.ParticipantID,
				"error", err,
			)
		}
	}

	// Forward incoming RTP packets to subscribers.
	go func() {
		if err := track.ForwardRTP(); err != nil {
			slog.Debug(
				"RTP forwarding ended",
				"trackId", track.ID,
				"error", err,
			)
		}
		_ = manager.RemovePublishedTrack(track.ID)
	}()

	return nil
}

func (manager *Manager) RemovePublishedTrack(trackID string) error {
	manager.mu.Lock()
	track, exists := manager.tracks[trackID]
	if exists {
		delete(manager.tracks, trackID)
	}
	manager.mu.Unlock()

	if !exists {
		return nil
	}

	track.Stop()

	manager.mu.RLock()
	subscribers := make([]*Peer, 0)
	for _, peer := range manager.peers {
		if peer.MeetingCode == track.MeetingCode &&
			peer.ParticipantID != track.OwnerParticipantID {
			subscribers = append(subscribers, peer)
		}
	}
	manager.mu.RUnlock()

	for _, subscriber := range subscribers {
		if err := subscriber.RemoveOutgoingTrack(trackID); err != nil {
			continue
		}

		_ = subscriber.Negotiate()
	}

	return nil
}

func (manager *Manager) RemovePeer(
	meetingCode string,
	participantID string,
) error {
	key := peerKey(meetingCode, participantID)

	manager.mu.Lock()
	peer, exists := manager.peers[key]
	if exists {
		delete(manager.peers, key)
	}
	manager.mu.Unlock()

	if !exists {
		return nil
	}

	closeErr := peer.Close()

	manager.mu.RLock()
	ownedTracks := make([]string, 0)
	for id, track := range manager.tracks {
		if track.MeetingCode == meetingCode &&
			track.OwnerParticipantID == participantID {
			ownedTracks = append(ownedTracks, id)
		}
	}
	manager.mu.RUnlock()

	for _, id := range ownedTracks {
		_ = manager.RemovePublishedTrack(id)
	}

	return closeErr
}
