package routes

import (
	"net/http"
	"server/server/handler"
	"server/server/utils"

	"github.com/gin-gonic/gin"
)

func SetupRouter(meetingHandler *handler.MeetingHandler) *gin.Engine {
	engine := gin.Default()

	engine.NoRoute(func(ctx *gin.Context) {
		utils.NotFound(ctx, "route not found")
	})

	engine.NoMethod(func(ctx *gin.Context) {
		utils.Error(ctx, http.StatusMethodNotAllowed, "method not allowed")
	})

	api := engine.Group("/api/v1")
	{
		meetings := api.Group("/meetings")
		{
			meetings.POST("/", meetingHandler.CreateMeeting)
		}
	}
}
