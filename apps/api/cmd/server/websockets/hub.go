package websockets

import (
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
)

const (
	maxChatMessageLength = 1000
	minReactionInterval  = 150 * time.Millisecond
)

var allowedReactions = map[string]bool{
	"👍": true, "👏": true, "❤️": true, "😂": true, "🎉": true, "😮": true,
}

type Hub struct {
	clients map[string]map[*Client]bool
	mu      sync.RWMutex
}

func NewHub() *Hub {
	return &Hub{clients: make(map[string]map[*Client]bool)}
}

func (hub *Hub) RegisterClient(client *Client) {
	hub.mu.Lock()
	defer hub.mu.Unlock()

	if hub.clients[client.MeetingId] == nil {
		hub.clients[client.MeetingId] = make(map[*Client]bool)
	}

	hub.clients[client.MeetingId][client] = true
	slog.Info(
		"client registered",
		"participantId", client.Id,
		"meetingId", client.MeetingId,
	)
}

func (hub *Hub) UnregisterClient(client *Client) {
	hub.mu.Lock()
	defer hub.mu.Unlock()

	participants, exists := hub.clients[client.MeetingId]
	if !exists {
		return
	}

	delete(participants, client)

	if len(participants) == 0 {
		delete(hub.clients, client.MeetingId)
	}
	slog.Info(
		"client unregistered",
		"participantId", client.Id,
		"meetingId", client.MeetingId,
	)
}

func (hub *Hub) Broadcast(
	meetingId string,
	event Event,
	except *Client,
) {
	hub.mu.RLock()
	defer hub.mu.RUnlock()

	message, err := json.Marshal(event)
	if err != nil {
		slog.Error(
			"failed to marshal websocket event",
			"error", err,
			"type", event.Type,
		)
		return
	}

	participants := hub.clients[meetingId]
	for client := range participants {
		if client == except {
			continue
		}
		select {
		case client.Send <- message:
		default:
			slog.Warn(
				"client send buffer full",
				"participantId", client.Id,
				"meetingId", meetingId,
			)
		}
	}
}

func (hub *Hub) Send(client *Client, event Event) {
	message, err := json.Marshal(event)
	if err != nil {
		slog.Error(
			"failed to marshal websocket event",
			"error", err,
			"type", event.Type,
		)
		return
	}
	select {
	case client.Send <- message:
	default:
		slog.Warn(
			"client send buffer full",
			"participantId", client.Id,
		)
	}
}

func (hub *Hub) HandleEvent(client *Client, event Event) {
	switch event.Type {
	case EventChatMessage:
		hub.handleChatMessage(client, event)
	case EventReaction:
		hub.handleReaction(client, event)
	case EventHandRaise:
		hub.handleHandRaise(client, event)
	default:
		hub.sendError(client, "UNKNOWN_EVENT", "unknown event type")
	}
}

// Chat is never persisted: it is stamped with the sender's identity and
// broadcast to whoever is in the meeting right now, sender included.
func (hub *Hub) handleChatMessage(client *Client, event Event) {
	var payload ChatMessagePayload
	if err := decodeData(event.Data, &payload); err != nil {
		hub.sendError(client, "INVALID_CHAT_MESSAGE", "invalid chat message")
		return
	}

	message := strings.TrimSpace(payload.Message)
	if message == "" {
		hub.sendError(client, "INVALID_CHAT_MESSAGE", "message is required")
		return
	}
	if utf8.RuneCountInString(message) > maxChatMessageLength {
		hub.sendError(client, "INVALID_CHAT_MESSAGE", "message must be 1000 characters or less")
		return
	}

	hub.Broadcast(
		client.MeetingId,
		Event{
			Type: EventChatMessage,
			Data: ChatMessageData{
				Id:            uuid.NewString(),
				ParticipantId: client.Id,
				Name:          client.Name,
				Message:       message,
				SentAt:        time.Now().UnixMilli(),
			},
		},
		nil,
	)
}

// The sender shows its own reaction immediately, so it is excluded here.
func (hub *Hub) handleReaction(client *Client, event Event) {
	var payload ReactionPayload
	if err := decodeData(event.Data, &payload); err != nil || !allowedReactions[payload.Emoji] {
		hub.sendError(client, "INVALID_REACTION", "unsupported reaction")
		return
	}

	// ReadPump handles one event at a time, so no locking is needed.
	now := time.Now()
	if now.Sub(client.lastReactionAt) < minReactionInterval {
		return
	}
	client.lastReactionAt = now

	hub.Broadcast(
		client.MeetingId,
		Event{
			Type: EventReaction,
			Data: ReactionData{
				ParticipantId: client.Id,
				Name:          client.Name,
				Emoji:         payload.Emoji,
			},
		},
		client,
	)
}

// Hand state lives only in memory, on the connected client, and goes away
// when the client disconnects. The sender already shows its own state.
func (hub *Hub) handleHandRaise(client *Client, event Event) {
	var payload HandRaisePayload
	if err := decodeData(event.Data, &payload); err != nil {
		hub.sendError(client, "INVALID_HAND_RAISE", "invalid hand raise")
		return
	}
	if client.handRaised.Swap(payload.Raised) == payload.Raised {
		return
	}

	hub.Broadcast(
		client.MeetingId,
		Event{
			Type: EventHandRaise,
			Data: HandRaiseData{
				ParticipantId: client.Id,
				Name:          client.Name,
				Raised:        payload.Raised,
			},
		},
		client,
	)
}

// SendRaisedHands tells a newly joined client whose hands are already up.
func (hub *Hub) SendRaisedHands(client *Client) {
	hub.mu.RLock()
	var raised []HandRaiseData
	for other := range hub.clients[client.MeetingId] {
		if other != client && other.handRaised.Load() {
			raised = append(raised, HandRaiseData{
				ParticipantId: other.Id,
				Name:          other.Name,
				Raised:        true,
			})
		}
	}
	hub.mu.RUnlock()

	for _, data := range raised {
		hub.Send(client, Event{Type: EventHandRaise, Data: data})
	}
}

func (hub *Hub) sendError(client *Client, code string, message string) {
	hub.Send(
		client,
		Event{
			Type: EventError,
			Data: ErrorData{
				Code:    code,
				Message: message,
			},
		},
	)
}

func decodeData(data any, target any) error {
	encoded, err := json.Marshal(data)
	if err != nil {
		return err
	}
	return json.Unmarshal(encoded, target)
}
