package websockets

import (
	"log"
	"log/slog"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 1 << 20 // 1 MiB
)

type Client struct {
	Id        string
	Name      string
	MeetingId string

	Conn *websocket.Conn
	Hub  *Hub

	Send         chan []byte
	OnDisconnect func(client *Client)

	OnEvent func(client *Client, event Event)

	lastReactionAt time.Time
	// Read by other clients' goroutines when someone joins.
	handRaised atomic.Bool
}

func (client *Client) ReadPump() {
	defer func() {
		client.Hub.UnregisterClient(client)
		if client.OnDisconnect != nil {
			client.OnDisconnect(client)
		}
		client.Hub.Broadcast(
			client.MeetingId,
			Event{
				Type: EventMeetingLeft,
				Data: ParticipantData{
					Name:          client.Name,
					ParticipantId: client.Id,
				},
			},
			client,
		)
		err := client.Conn.Close()
		if err != nil {
			log.Printf("Error closing client: %v", err)
		}
	}()

	client.Conn.SetReadLimit(maxMessageSize)

	if err := client.Conn.SetReadDeadline(
		time.Now().Add(pongWait),
	); err != nil {
		slog.Error(
			"failed to set websocket read deadline",
			"error", err,
		)
		return
	}

	client.Conn.SetPongHandler(func(string) error {
		return client.Conn.SetReadDeadline(
			time.Now().Add(pongWait),
		)
	})

	for {
		var event Event
		if err := client.Conn.ReadJSON(&event); err != nil {
			break
		}
		if client.OnEvent != nil {
			client.OnEvent(client, event)
		} else {
			client.Hub.HandleEvent(client, event)
		}
	}
}

func (client *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)

	defer func() {
		ticker.Stop()
		err := client.Conn.Close()
		if err != nil {
			return
		}
	}()

	for {
		select {
		case message, ok := <-client.Send:
			if err := client.Conn.SetWriteDeadline(
				time.Now().Add(writeWait),
			); err != nil {
				return
			}

			if !ok {
				_ = client.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := client.Conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

		case <-ticker.C:
			if err := client.Conn.SetWriteDeadline(
				time.Now().Add(writeWait),
			); err != nil {
				return
			}

			if err := client.Conn.WriteMessage(
				websocket.PingMessage,
				nil,
			); err != nil {
				return
			}
		}
	}
}
