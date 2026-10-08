package main

import (
	"config"
	"context"
	"log"
	"os/signal"
	"server/server/handler"
	"server/server/redis"
	"server/server/routes"
	"server/server/services"
	"server/server/websockets"
	"syscall"

	redis2 "github.com/redis/go-redis/v9"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	cfg := config.Load()
	rdb, err := redis.NewClient(ctx, cfg)
	if err != nil {
		log.Fatal(err)
	}
	defer func(rdb *redis2.Client) {
		err := rdb.Close()
		if err != nil {
			log.Fatal(err)
		}
	}(rdb)
	// ws
	hub := websockets.NewHub()

	// Services
	meetingService := services.NewMeetingService(rdb)

	// Handlers
	meetingHandler := handler.NewMeetingHandler(meetingService)
	websocketHandler := handler.NewWebSocketHandler(hub, meetingService)

	router := routes.SetupRouter(meetingHandler, websocketHandler)

	log.Println("Starting HTTP & WebSocket server on :8080...")

	if err := router.Run(":8080"); err != nil {
		log.Fatalf("Failed to run server: %v", err)
	}

}
