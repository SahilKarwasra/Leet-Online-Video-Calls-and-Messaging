package websockets

type EventType string

const (
	EventParticipantJoinedMeeting EventType = "participant_joined_meeting"
	EventParticipantLeftMeeting   EventType = "participant_left_meeting"

	ChatMessageReceived EventType = "chat_message_received"
)

type Event struct {
	Type EventType `json:"type"`
	Data any       `json:"data"`
}

type ParticipantJoinPayload struct {
	Name string `json:"name"`
}

type ParticipantData struct {
	Name          string `json:"name"`
	ParticipantId string `json:"participant_id"`
}

type ErrorData struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
