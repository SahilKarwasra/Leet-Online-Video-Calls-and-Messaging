package meetings

import "time"

type CreateRequest struct {
	HostName string `json:"host_name" binding:"required,min=1,max=50"`
}

type MeetingResponse struct {
	ID        string    `json:"id"`
	Code      string    `json:"code"`
	HostName  string    `json:"host_name"`
	Status    Status    `json:"status"`
	CreatedAt time.Time `json:"created_at"`
}

type CreateResponse struct {
	Meeting   MeetingResponse `json:"meeting"`
	HostToken string          `json:"host_token"`
}

func toResponse(m *Meeting) MeetingResponse {
	return MeetingResponse{m.ID, m.Code, m.HostName, m.Status, m.CreatedAt}
}
