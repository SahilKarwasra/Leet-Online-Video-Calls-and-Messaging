package meetings

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"strconv"
	"time"

	"github.com/google/uuid"
	goredis "github.com/redis/go-redis/v9"

	redisinfra "github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/infrastructure/redis"
)

const meetingTTL = 24 * time.Hour

var ErrNotFound = errors.New("meeting not found")

type Service struct {
	rdb *goredis.Client
}

func NewService(rdb *goredis.Client) *Service { return &Service{rdb: rdb} }

func (s *Service) Create(ctx context.Context, hostName string) (*Meeting, error) {
	id := "m_" + uuid.NewString()[:8]

	code := ""
	for i := 0; i < 5; i++ {
		c, err := generateCode()
		if err != nil {
			return nil, err
		}
		ok, err := s.rdb.SetNX(ctx, redisinfra.MeetingCodeKey(c), id, meetingTTL).Result()
		if err != nil {
			return nil, err
		}
		if ok {
			code = c
			break
		}
	}
	if code == "" {
		return nil, errors.New("could not allocate meeting code")
	}

	token, err := randomHex(16)
	if err != nil {
		return nil, err
	}

	m := &Meeting{
		ID: id, Code: code, HostName: hostName, HostToken: token,
		LiveKitRoom: id, Status: StatusActive, CreatedAt: time.Now().UTC(),
	}

	key := redisinfra.MeetingKey(id)
	pipe := s.rdb.TxPipeline()
	pipe.HSet(ctx, key, map[string]any{
		"id": m.ID, "code": m.Code, "host_name": m.HostName,
		"host_token": m.HostToken, "livekit_room": m.LiveKitRoom,
		"status": string(m.Status), "created_at": m.CreatedAt.Unix(),
	})
	pipe.Expire(ctx, key, meetingTTL)
	if _, err := pipe.Exec(ctx); err != nil {
		return nil, err
	}
	return m, nil
}

func (s *Service) GetByCode(ctx context.Context, code string) (*Meeting, error) {
	id, err := s.rdb.Get(ctx, redisinfra.MeetingCodeKey(code)).Result()
	if errors.Is(err, goredis.Nil) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return s.getByID(ctx, id)
}

func (s *Service) getByID(ctx context.Context, id string) (*Meeting, error) {
	h, err := s.rdb.HGetAll(ctx, redisinfra.MeetingKey(id)).Result()
	if err != nil {
		return nil, err
	}
	if len(h) == 0 {
		return nil, ErrNotFound
	}
	created, _ := strconv.ParseInt(h["created_at"], 10, 64)
	return &Meeting{
		ID: h["id"], Code: h["code"], HostName: h["host_name"],
		HostToken: h["host_token"], LiveKitRoom: h["livekit_room"],
		Status: Status(h["status"]), CreatedAt: time.Unix(created, 0).UTC(),
	}, nil
}

func randomHex(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// Meet-style code: abc-defg-hij
func generateCode() (string, error) {
	const letters = "abcdefghijklmnopqrstuvwxyz"
	b := make([]byte, 10)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	out := make([]byte, 0, 12)
	for i, v := range b {
		if i == 3 || i == 7 {
			out = append(out, '-')
		}
		out = append(out, letters[int(v)%len(letters)])
	}
	return string(out), nil
}
