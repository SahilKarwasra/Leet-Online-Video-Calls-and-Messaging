package websockets

import (
	"encoding/json"
	"log/slog"
	"sync"
)

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
		"client registered",
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

func Send(client *Client, event Event) {
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
