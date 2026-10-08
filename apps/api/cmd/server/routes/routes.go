package routes

import (
	"net/http"
	"server/server/handler"
	"server/server/utils"

	"github.com/gin-gonic/gin"
)

func SetupRouter(
	meetingHandler *handler.MeetingHandler,
	websocketHandler *handler.WebSocketHandler,
) *gin.Engine {
	engine := gin.Default()

	engine.NoRoute(func(ctx *gin.Context) {
		utils.NotFound(ctx, "route not found")
	})

	engine.NoMethod(func(ctx *gin.Context) {
		utils.Error(ctx, http.StatusMethodNotAllowed, "method not allowed")
	})

	api := engine.Group("/api/v1")
	{
		api.POST("/meeting", meetingHandler.CreateMeeting)
	}
	engine.GET("/ws/meetings/:code",
		websocketHandler.Connect,
	)

	return engine
}
