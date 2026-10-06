package routes

import (
	"net/http"

	"github.com/gin-gonic/gin"
	goredis "github.com/redis/go-redis/v9"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/config"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/infrastructure/livekit"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/meetings"
)

type Deps struct {
	Config config.Config
	Redis  *goredis.Client
	Tokens *livekit.TokenIssuer
}

func NewRouter(d Deps) *gin.Engine {
	router := gin.Default()

	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"message": "OK"})
	})

	meetings.RegisterRoutes(router, meetings.NewHandler(meetings.NewService(d.Redis)))

	return router
}
