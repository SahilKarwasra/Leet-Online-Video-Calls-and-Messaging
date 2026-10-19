package websockets

type EventType string

const (
	EventMeetingJoin               EventType = "meeting.join"
	EventMeetingJoined             EventType = "meeting.joined"             // sent only to the joining client
	EventParticipantJoined         EventType = "meeting.participant_joined" // broadcast to everyone else
	EventMeetingLeft               EventType = "meeting.left"
	EventAlreadyExistsParticipants EventType = "meeting.already_exists_participants"

	EventChatMessage EventType = "chat.message"       // client sends ChatMessagePayload, server broadcasts ChatMessageData
	EventReaction    EventType = "meeting.reaction"   // client sends ReactionPayload, server broadcasts ReactionData
	EventHandRaise   EventType = "meeting.hand_raise" // client sends HandRaisePayload, server broadcasts HandRaiseData
	EventError       EventType = "error"

	EventWebRTCOffer        EventType = "webrtc.offer"
	EventWebRTCAnswer       EventType = "webrtc.answer"
	EventWebRTCICECandidate EventType = "webrtc.ice_candidate"
)

type SDPDescriptionPayload struct {
	Type string `json:"type"`
	SDP  string `json:"sdp"`
}

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

type ChatMessagePayload struct {
	Message string `json:"message"`
}

type ChatMessageData struct {
	Id            string `json:"id"`
	ParticipantId string `json:"participant_id"`
	Name          string `json:"name"`
	Message       string `json:"message"`
	SentAt        int64  `json:"sent_at"` // unix milliseconds
}

type ReactionPayload struct {
	Emoji string `json:"emoji"`
}

type ReactionData struct {
	ParticipantId string `json:"participant_id"`
	Name          string `json:"name"`
	Emoji         string `json:"emoji"`
}

type HandRaisePayload struct {
	Raised bool `json:"raised"`
}

type HandRaiseData struct {
	ParticipantId string `json:"participant_id"`
	Name          string `json:"name"`
	Raised        bool   `json:"raised"`
}

type ErrorData struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
