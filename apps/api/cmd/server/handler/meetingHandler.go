package handler

import (
	"net/http"
	"server/server/services"
	"server/server/utils"

	"github.com/gin-gonic/gin"
)

type MeetingHandler struct {
	meetingService *services.MeetingService
}

func NewMeetingHandler(service *services.MeetingService) *MeetingHandler {
	return &MeetingHandler{
		meetingService: service,
	}
}

func (handler *MeetingHandler) CreateMeeting(ctx *gin.Context) {
	meeting, err := handler.meetingService.CreateMeeting(
		ctx.Request.Context(),
	)

	if err != nil {
		utils.InternalServerError(ctx, err.Error())
		return
	}
	utils.Success(
		ctx,
		http.StatusCreated,
		"meeting created successfully",
		meeting,
	)

}
