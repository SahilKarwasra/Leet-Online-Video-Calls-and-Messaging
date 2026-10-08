package websockets

type EventType string

const (
	EventMeetingJoin      EventType = "meeting.join"
	EventMeetingJoined    EventType = "meeting.joined"     // sent only to the joining client
	EventParticipantJoined EventType = "meeting.participant_joined" // broadcast to everyone else
	EventMeetingLeft      EventType = "meeting.left"

	EventChatMessage EventType = "chat.message"
	EventError       EventType = "error"
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
