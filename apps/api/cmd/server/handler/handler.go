package handler

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"server/server/websockets"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type WebSocketHandler struct {
	hub *websockets.Hub
}

func NewWebSocketHandler(hub *websockets.Hub) *WebSocketHandler {
	return &WebSocketHandler{
		hub: hub,
	}
}

var Upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

func (handler *WebSocketHandler) Connect(c *gin.Context) {
	meetingCode := c.Param("code")
	if strings.TrimSpace(meetingCode) == "" {
		c.JSON(
			http.StatusBadRequest,
			gin.H{
				"error": "meeting code is required",
			},
		)
		return
	}
	conn, err := Upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		slog.Error(
			"failed to upgrade websocket connection",
			"error", err,
		)
		return
	}
	defer func(conn *websocket.Conn) {
		err := conn.Close()
		if err != nil {
			slog.Error(
				"failed to close websocket connection",
				"error", err,
			)
		}
	}(conn)

	client := &websockets.Client{
		Id:        uuid.NewString(),
		MeetingId: meetingCode,
		Conn:      conn,
		Hub:       handler.hub,
		Send:      make(chan []byte, 256),
	}
	go client.WritePump()
	handler.handleJoin(client)
}

func (handler *WebSocketHandler) handleJoin(client *websockets.Client) {
	var event websockets.Event
	if err := client.Conn.ReadJSON(&event); err != nil {
		_ = client.Conn.Close()
		return
	}
	if event.Type != websockets.EventParticipantJoinedMeeting {
		handler.sendErrorAndClose(
			client,
			"INVALID_FIRST_EVENT",
			"first event must be join",
		)
		return
	}

	dataBytes, err := json.Marshal(event.Data)
	if err != nil {
		handler.sendErrorAndClose(
			client,
			"INVALID_JOIN_DATA",
			"invalid join data",
		)
		return
	}

	var joinData websockets.ParticipantJoinPayload
	if err := json.Unmarshal(
		dataBytes,
		&joinData,
	); err != nil {
		handler.sendErrorAndClose(
			client,
			"INVALID_JOIN_DATA",
			"invalid join data",
		)
		return
	}

	name := strings.TrimSpace(joinData.Name)

	if name == "" {
		handler.sendErrorAndClose(
			client,
			"INVALID_NAME",
			"name is required",
		)
		return
	}

	if len(name) > 100 {
		handler.sendErrorAndClose(
			client,
			"INVALID_NAME",
			"name must be 100 characters or less",
		)
		return
	}

	client.Name = name

	handler.hub.RegisterClient(client)

	handler.hub.Send(
		client,
		websockets.Event{
			Type: "meeting.joined",
			Data: websockets.ParticipantData{
				ParticipantId: client.Id,
				Name:          client.Name,
			},
		},
	)

}

func (handler *WebSocketHandler) sendErrorAndClose(
	client *websockets.Client,
	code string,
	message string,
) {
	handler.hub.Send(
		client,
		websockets.Event{
			Type: "error",
			Data: websockets.ErrorData{
				Code:    code,
				Message: message,
			},
		},
	)

	_ = client.Conn.WriteControl(
		websocket.CloseMessage,
		websocket.FormatCloseMessage(
			websocket.ClosePolicyViolation,
			message,
		),
		time.Now().Add(time.Second),
	)

	_ = client.Conn.Close()
}
