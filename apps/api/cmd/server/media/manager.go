package media

import (
	"fmt"
	"sync"
)

type Manager struct {
	mu    sync.RWMutex
	peers map[string]*Peer
}

func NewManager() *Manager {
	return &Manager{
		peers: make(map[string]*Peer),
	}
}

func peerKey(participantId string, meetingCode string) string {
	return meetingCode + ":" + participantId
}

func (manager *Manager) AddPeer(peer *Peer) error {
	key := peerKey(peer.MeetingCode, peer.ParticipantId)
	manager.mu.Lock()
	defer manager.mu.Unlock()

	if _, exists := manager.peers[key]; exists {
		return fmt.Errorf(
			"peer already exists for participant %s in meeting %s",
			peer.ParticipantId,
			peer.MeetingCode,
		)
	}

	manager.peers[key] = peer
	return nil
}

func (manager *Manager) RemovePeer(peer *Peer) error {
	key := peerKey(peer.MeetingCode, peer.ParticipantId)
	manager.mu.Lock()
	defer manager.mu.Unlock()

	peer, exists := manager.peers[key]
	if exists {
		delete(manager.peers, key)
	}
	manager.mu.Unlock()

	if !exists {
		return nil
	}

	return peer.Close()
}

func (manager *Manager) GetPeer(
	meetingCode string,
	participantID string,
) (*Peer, bool) {
	key := peerKey(meetingCode, participantID)

	manager.mu.RLock()
	defer manager.mu.RUnlock()

	peer, exists := manager.peers[key]
	return peer, exists
}
