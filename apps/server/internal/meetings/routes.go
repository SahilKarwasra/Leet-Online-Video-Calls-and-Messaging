package meetings

import "github.com/gin-gonic/gin"

func RegisterRoutes(r *gin.Engine, handler *Handler) {
	meetings := r.Group("/meetings")

	meetings.POST("", handler.Create)
	meetings.GET("/:code", handler.Get)
}
