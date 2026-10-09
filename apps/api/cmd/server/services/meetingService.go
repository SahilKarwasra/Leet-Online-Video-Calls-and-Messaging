package services

import (
	"context"
	"encoding/json"
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

func (ms *MeetingService) AddParticipant(ctx context.Context, meetingCode string, participantId string, name string) error {
	participantsKey := "meeting:participants:" + meetingCode
	p := Participant{
		ParticipantId: participantId,
		Name:          name,
	}
	data, err := json.Marshal(p)
	if err != nil {
		return err
	}

	pipe := ms.rdb.TxPipeline()
	pipe.HSet(ctx, participantsKey, participantId, data)
	pipe.Expire(ctx, participantsKey, 24*time.Hour)
	_, err = pipe.Exec(ctx)
	return err
}

func (ms *MeetingService) RemoveParticipant(ctx context.Context, meetingCode string, participantId string) error {
	participantsKey := "meeting:participants:" + meetingCode
	return ms.rdb.HDel(ctx, participantsKey, participantId).Err()
}

func (ms *MeetingService) GetParticipants(ctx context.Context, meetingCode string) ([]Participant, error) {
	participantsKey := "meeting:participants:" + meetingCode
	vals, err := ms.rdb.HVals(ctx, participantsKey).Result()
	if err != nil {
		return nil, err
	}

	participants := make([]Participant, 0, len(vals))
	for _, val := range vals {
		var p Participant
		if err := json.Unmarshal([]byte(val), &p); err != nil {
			continue
		}
		participants = append(participants, p)
	}

	return participants, nil
}
