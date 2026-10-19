package handler

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"server/server/media"
	"server/server/services"
	"server/server/websockets"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/pion/webrtc/v4"
)

type WebSocketHandler struct {
	hub            *websockets.Hub
	meetingService *services.MeetingService
	mediaManager   *media.Manager
}

func NewWebSocketHandler(
	hub *websockets.Hub,
	meetingService *services.MeetingService,
	mediaManager *media.Manager,
) *WebSocketHandler {
	return &WebSocketHandler{
		hub:            hub,
		meetingService: meetingService,
		mediaManager:   mediaManager,
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
			if err := handler.mediaManager.RemovePeer(
				c.MeetingId,
				c.Id,
			); err != nil {
				slog.Error("failed to remove media peer", "error", err)
			}
			if err := handler.meetingService.RemoveParticipant(context.Background(), c.MeetingId, c.Id); err != nil {
				slog.Error(
					"failed to remove participant from redis",
					"error", err,
				)
			}
		},
		OnEvent: handler.handleClientEvent,
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

	handler.hub.SendRaisedHands(client)

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

func (handler *WebSocketHandler) handleClientEvent(
	client *websockets.Client,
	event websockets.Event,
) {
	switch event.Type {
	case websockets.EventWebRTCOffer:
		handler.handleWebRTCOffer(client, event)

	case websockets.EventWebRTCAnswer:
		handler.handleWebRTCAnswer(client, event)

	case websockets.EventWebRTCICECandidate:
		handler.handleICECandidate(client, event)

	default:
		handler.hub.HandleEvent(client, event)
	}
}

func (handler *WebSocketHandler) sendWebRTCError(
	client *websockets.Client,
	code string,
	message string,
) {
	handler.hub.Send(client, websockets.Event{
		Type: websockets.EventError,
		Data: websockets.ErrorData{
			Code:    code,
			Message: message,
		},
	})
}

func decodeEventData(data any, target any) error {
	encoded, err := json.Marshal(data)
	if err != nil {
		return err
	}
	return json.Unmarshal(encoded, target)
}

func (handler *WebSocketHandler) getOrCreatePeer(
	client *websockets.Client,
) (*media.Peer, error) {
	if peer, exists := handler.mediaManager.GetPeer(
		client.MeetingId,
		client.Id,
	); exists {
		return peer, nil
	}

	peer, err := media.NewPeer(client.Id, client.MeetingId)
	if err != nil {
		return nil, err
	}

	peer.OnICECandidate = func(candidate webrtc.ICECandidateInit) {
		handler.hub.Send(client, websockets.Event{
			Type: websockets.EventWebRTCICECandidate,
			Data: candidate,
		})
	}

	peer.OnTrack = func(track *webrtc.TrackRemote) {
		if err := handler.mediaManager.PublishTrack(peer, track); err != nil {
			slog.Error(
				"failed to publish WebRTC track",
				"error", err,
				"participantId", client.Id,
				"meetingCode", client.MeetingId,
				"trackId", track.ID(),
			)
		}
	}

	peer.OnLocalOffer = func(sdp string) {
		handler.hub.Send(client, websockets.Event{
			Type: websockets.EventWebRTCOffer,
			Data: websockets.SDPDescriptionPayload{
				Type: "offer",
				SDP:  sdp,
			},
		})
	}

	if err := handler.mediaManager.AddPeer(peer); err != nil {
		_ = peer.Close()

		// Another offer may have created the peer concurrently.
		if existing, exists := handler.mediaManager.GetPeer(
			client.MeetingId,
			client.Id,
		); exists {
			return existing, nil
		}

		return nil, err
	}

	return peer, nil
}

func (handler *WebSocketHandler) handleWebRTCOffer(
	client *websockets.Client,
	event websockets.Event,
) {
	var payload websockets.SDPDescriptionPayload

	if err := decodeEventData(event.Data, &payload); err != nil ||
		payload.Type != "offer" ||
		strings.TrimSpace(payload.SDP) == "" {
		handler.sendWebRTCError(
			client,
			"INVALID_OFFER",
			"a valid SDP offer is required",
		)
		return
	}

	peer, err := handler.getOrCreatePeer(client)
	if err != nil {
		slog.Error("failed to create media peer", "error", err)
		handler.sendWebRTCError(
			client,
			"WEBRTC_PEER_FAILED",
			"could not create WebRTC connection",
		)
		return
	}

	if err := peer.SetRemoteOffer(payload.SDP); err != nil {
		slog.Error("failed to apply remote offer", "error", err)
		handler.sendWebRTCError(
			client,
			"INVALID_REMOTE_OFFER",
			"could not apply WebRTC offer",
		)
		return
	}

	// Attach tracks from existing participants before creating the answer.
	if err := handler.mediaManager.AttachExistingTracks(peer); err != nil {
		slog.Error("failed to attach existing tracks", "error", err)
		handler.sendWebRTCError(
			client,
			"TRACK_ATTACHMENT_FAILED",
			"could not attach existing media tracks",
		)
		return
	}

	answerSDP, err := peer.CreateAnswer()
	if err != nil {
		slog.Error("failed to create WebRTC answer", "error", err)
		handler.sendWebRTCError(
			client,
			"WEBRTC_NEGOTIATION_FAILED",
			"could not create WebRTC answer",
		)
		return
	}

	handler.hub.Send(client, websockets.Event{
		Type: websockets.EventWebRTCAnswer,
		Data: websockets.SDPDescriptionPayload{
			Type: "answer",
			SDP:  answerSDP,
		},
	})

	// The answer can only reuse the m-lines in the client's offer, so with more
	// than one existing participant some attached tracks are still unnegotiated.
	// Send a follow-up offer so the client receives all of them.
	if err := peer.Negotiate(); err != nil {
		slog.Error("failed to renegotiate after answer", "error", err)
	}
}

func (handler *WebSocketHandler) handleWebRTCAnswer(
	client *websockets.Client,
	event websockets.Event,
) {
	var payload websockets.SDPDescriptionPayload

	if err := decodeEventData(event.Data, &payload); err != nil ||
		payload.Type != "answer" ||
		strings.TrimSpace(payload.SDP) == "" {
		handler.sendWebRTCError(
			client,
			"INVALID_ANSWER",
			"a valid SDP answer is required",
		)
		return
	}

	peer, exists := handler.mediaManager.GetPeer(
		client.MeetingId,
		client.Id,
	)
	if !exists {
		handler.sendWebRTCError(
			client,
			"PEER_NOT_FOUND",
			"no WebRTC connection exists for this participant",
		)
		return
	}

	if err := peer.SetRemoteAnswer(payload.SDP); err != nil {
		slog.Error("failed to apply WebRTC answer", "error", err)
		handler.sendWebRTCError(
			client,
			"INVALID_REMOTE_ANSWER",
			"could not apply WebRTC answer",
		)
	}
}

func (handler *WebSocketHandler) handleICECandidate(
	client *websockets.Client,
	event websockets.Event,
) {
	var candidate webrtc.ICECandidateInit

	if err := decodeEventData(event.Data, &candidate); err != nil {
		handler.sendWebRTCError(
			client,
			"INVALID_ICE_CANDIDATE",
			"invalid ICE candidate payload",
		)
		return
	}

	peer, exists := handler.mediaManager.GetPeer(
		client.MeetingId,
		client.Id,
	)
	if !exists {
		// A candidate can arrive before the first offer. Do not silently
		// create a PeerConnection without knowing its intended negotiation.
		handler.sendWebRTCError(
			client,
			"PEER_NOT_FOUND",
			"send a WebRTC offer before ICE candidates",
		)
		return
	}

	if err := peer.AddICECandidate(candidate); err != nil {
		slog.Error("failed to add ICE candidate", "error", err)
		handler.sendWebRTCError(
			client,
			"ICE_CANDIDATE_FAILED",
			"could not apply ICE candidate",
		)
	}
}
