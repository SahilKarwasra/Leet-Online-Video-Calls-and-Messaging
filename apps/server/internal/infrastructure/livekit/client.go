package livekit

import (
	"time"

	"github.com/livekit/protocol/auth"
	"github.com/sahilkarwasra/leetcodevideocallandmessaging/internal/config"
)

type TokenIssuer struct {
	apiKey, apiSecret, url string
}

func NewTokenIssuer(cfg *config.Config) *TokenIssuer {
	return &TokenIssuer{
		apiKey:    cfg.LiveKitAPIKey,
		apiSecret: cfg.LiveKitAPISecret,
		url:       cfg.LiveKitURL,
	}
}

func (t *TokenIssuer) URL() string {
	return t.url
}

func (t *TokenIssuer) Issue(room, identity, name string, ttl time.Duration) (string, error) {
	at := auth.NewAccessToken(t.apiKey, t.apiSecret)
	at.SetVideoGrant(&auth.VideoGrant{RoomJoin: true, Room: room}).SetIdentity(identity).SetName(name).SetValidFor(ttl)
	return at.ToJWT()
}
