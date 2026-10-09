package handler

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"server/server/services"
	"server/server/websockets"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type WebSocketHandler struct {
	hub            *websockets.Hub
	meetingService *services.MeetingService
}

func NewWebSocketHandler(hub *websockets.Hub, meetingService *services.MeetingService) *WebSocketHandler {
	return &WebSocketHandler{
		hub:            hub,
		meetingService: meetingService,
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

	// Validate the meeting exists in Redis before upgrading
	exists, err := handler.meetingService.MeetingExists(c.Request.Context(), meetingCode)
	if err != nil {
		slog.Error(
			"failed to check meeting existence",
			"error", err,
			"meetingCode", meetingCode,
		)
		c.JSON(
			http.StatusInternalServerError,
			gin.H{
				"error": "internal server error",
			},
		)
		return
	}
	if !exists {
		c.JSON(
			http.StatusNotFound,
			gin.H{
				"error": "meeting not found",
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

	client := &websockets.Client{
		Id:        uuid.NewString(),
		MeetingId: meetingCode,
		Conn:      conn,
		Hub:       handler.hub,
		Send:      make(chan []byte, 256),
		OnDisconnect: func(c *websockets.Client) {
			if err := handler.meetingService.RemoveParticipant(context.Background(), c.MeetingId, c.Id); err != nil {
				slog.Error(
					"failed to remove participant from redis",
					"error", err,
					"participantId", c.Id,
					"meetingCode", c.MeetingId,
				)
			}
		},
	}
	go client.WritePump()
	handler.handleJoin(c.Request.Context(), client)
}

func (handler *WebSocketHandler) handleJoin(ctx context.Context, client *websockets.Client) {
	var event websockets.Event
	if err := client.Conn.ReadJSON(&event); err != nil {
		_ = client.Conn.Close()
		return
	}
	if event.Type != websockets.EventMeetingJoin {
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

	existingParticipants, err := handler.meetingService.GetParticipants(ctx, client.MeetingId)
	if err != nil {
		slog.Error(
			"failed to get existing participants from redis",
			"error", err,
			"meetingCode", client.MeetingId,
		)
		existingParticipants = []services.Participant{}
	}

	if err := handler.meetingService.AddParticipant(ctx, client.MeetingId, client.Id, client.Name); err != nil {
		slog.Error(
			"failed to add participant to redis",
			"error", err,
			"participantId", client.Id,
			"meetingCode", client.MeetingId,
		)
	}

	handler.hub.RegisterClient(client)

	handler.hub.Send(
		client,
		websockets.Event{
			Type: websockets.EventMeetingJoined,
			Data: websockets.ParticipantData{
				ParticipantId: client.Id,
				Name:          client.Name,
			},
		},
	)

	handler.hub.Send(
		client,
		websockets.Event{
			Type: websockets.EventAlreadyExistsParticipants,
			Data: existingParticipants,
		},
	)

	handler.hub.Broadcast(
		client.MeetingId,
		websockets.Event{
			Type: websockets.EventParticipantJoined,
			Data: websockets.ParticipantData{
				ParticipantId: client.Id,
				Name:          client.Name,
			},
		},
		client, // exclude the joiner themselves
	)

	client.ReadPump()
}

func (handler *WebSocketHandler) sendErrorAndClose(
	client *websockets.Client,
	code string,
	message string,
) {
	handler.hub.Send(
		client,
		websockets.Event{
			Type: websockets.EventError,
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
