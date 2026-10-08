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

	// Also store a reverse lookup: code → meeting ID
	codeKey := "meeting_code:" + meetingCode
	err = ms.rdb.Set(ctx, codeKey, meetingID, 24*time.Hour).Err()
	if err != nil {
		return nil, err
	}

	err = ms.rdb.Expire(ctx, key, 24*time.Hour).Err()

	if err != nil {
		return nil, err
	}

	return meeting, nil

}

// MeetingExists checks whether a meeting with the given code exists and is active.
func (ms *MeetingService) MeetingExists(ctx context.Context, code string) (bool, error) {
	codeKey := "meeting_code:" + code
	exists, err := ms.rdb.Exists(ctx, codeKey).Result()
	if err != nil {
		return false, err
	}
	return exists > 0, nil
}

