package websockets

type EventType string

const (
	EventParticipantJoinedMeeting EventType = "participant_joined_meeting"
	EventParticipantLeftMeeting   EventType = "participant_left_meeting"
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
