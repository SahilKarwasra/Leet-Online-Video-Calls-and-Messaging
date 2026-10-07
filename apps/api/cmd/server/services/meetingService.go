package services

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

type MeetingService struct {
	rdb *redis.Client
}

func NewMeetingService(rdb *redis.Client) *MeetingService {
	return &MeetingService{rdb: rdb}
}

func (ms *MeetingService) CreateMeeting(ctx context.Context) (*MeetingResponse, error) {
	meetingID := uuid.NewString()
	meetingCode := uuid.NewString()[:8]

	meeting := &MeetingResponse{
		ID:     meetingID,
		Code:   meetingCode,
		Status: "active",
	}

	key := "meeting:" + meetingID
	err := ms.rdb.HSet(
		ctx,
		key,
		"id", meeting.ID,
		"code", meeting.Code,
		"status", meeting.Status,
	).Err()

	if err != nil {
		return nil, err
	}

	err = ms.rdb.Expire(ctx, key, 24*time.Hour).Err()

	if err != nil {
		return nil, err
	}

	return meeting, nil

}
