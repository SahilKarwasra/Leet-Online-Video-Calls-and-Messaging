package handler

import "server/server/services"

type MeetingHandler struct {
	meetingService *services.MeetingService
}

func NewMeetingHandler(service *services.MeetingService) *MeetingHandler {
	return &MeetingHandler{
		meetingService: service,
	}
}
