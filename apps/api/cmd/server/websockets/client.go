package websockets

import (
	"log"
	"log/slog"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 4096
)

type Client struct {
	Id        string
	Name      string
	MeetingId string

	Conn *websocket.Conn
	Hub  *Hub

	Send chan []byte
}

func ReadPump(client *Client) {
	defer func() {
		client.Hub.UnregisterClient(client)
		err := client.Conn.Close()
		if err != nil {
			log.Printf("Error closing client: %v", err)
			return
		}
		client.Hub.Broadcast(
			client.MeetingId,
			Event{
				Type: EventParticipantLeftMeeting,
				Data: ParticipantData{
					Name:          client.Name,
					ParticipantId: client.Id,
				},
			},
			client)
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
		client.Hub.HandleEvent(client, event)
	}
}
