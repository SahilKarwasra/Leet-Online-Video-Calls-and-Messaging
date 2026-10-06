package main

import (
	"context"
	"log"
	"os/signal"
	"syscall"
	"time"

	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/config"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/infrastructure/livekit"
	redisinfra "github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/infrastructure/redis"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/routes"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/server"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	cfg := config.Load()

	rdb, err := redisinfra.New(ctx, cfg)
	if err != nil {
		log.Fatalf("redis: %v", err)
	}
	defer rdb.Close()
	router := routes.NewRouter(routes.Deps{
		Config: cfg, Redis: rdb, Tokens: livekit.NewTokenIssuer(&cfg),
	})
	srv := server.New(":"+cfg.Port, router)

	go func() {
		log.Printf("listening on :%s", cfg.Port)
		if err := srv.Run(); err != nil {
			log.Fatalf("server: %v", err)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)

}
