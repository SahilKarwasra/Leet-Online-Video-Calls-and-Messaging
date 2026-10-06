package config

import (
	"os"
	"strconv"
)

type Config struct {
	Port          string
	AllowedOrigin string

	RedisAddr     string
	RedisPassword string
	RedisDB       int

	LiveKitURL       string
	LiveKitAPIKey    string
	LiveKitAPISecret string
}

func Load() Config {
	return Config{
		Port:          get("PORT", "8080"),
		AllowedOrigin: get("ALLOWED_ORIGIN", "http://localhost:5173"),

		RedisAddr:     get("REDIS_ADDR", "localhost:6379"),
		RedisPassword: get("REDIS_PASSWORD", ""),
		RedisDB:       getInt("REDIS_DB", 0),

		LiveKitURL:       get("LIVEKIT_URL", "ws://localhost:7880"),
		LiveKitAPIKey:    get("LIVEKIT_API_KEY", "devkey"),
		LiveKitAPISecret: get("LIVEKIT_API_SECRET", "secret"),
	}
}

func get(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}
