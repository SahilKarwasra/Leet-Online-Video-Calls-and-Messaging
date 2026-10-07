package redis

import (
	"config"
	"context"
	"time"

	"github.com/redis/go-redis/v9"
)

func NewClient(ctx context.Context, cfg config.Config) (*redis.Client, error) {
	client := redis.NewClient(&redis.Options{
		Addr:     cfg.RedisAddr,
		Password: cfg.RedisPassword,
		DB:       cfg.RedisDB,
	})
	pingCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	err := client.Ping(pingCtx).Err()
	if err != nil {
		return nil, err
	}
	return client, nil
}
