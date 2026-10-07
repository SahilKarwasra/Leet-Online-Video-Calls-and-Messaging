package services

import "github.com/redis/go-redis/v9"

type MeetingService struct {
	rdb *redis.Client
}

func NewMeetingService(rdb *redis.Client) *MeetingService {
	return &MeetingService{rdb: rdb}
}
