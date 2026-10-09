package services

type MeetingResponse struct {
	ID     string `json:"id"`
	Code   string `json:"code"`
	Status string `json:"status"`
}

type Participant struct {
	ParticipantId string `json:"participant_id"`
	Name          string `json:"name"`
}
