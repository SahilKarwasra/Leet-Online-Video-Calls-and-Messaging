package media

import (
	"fmt"
	"sync"

	"github.com/pion/webrtc/v4"
)

type PublishedTrack struct {
	ID                 string
	OwnerParticipantID string
	MeetingCode        string
	Remote             *webrtc.TrackRemote
	Local              *webrtc.TrackLocalStaticRTP

	stop     chan struct{}
	stopOnce sync.Once
}

func NewPublishedTrack(
	meetingCode string,
	participantID string,
	remote *webrtc.TrackRemote,
) (*PublishedTrack, error) {
	id := participantID + ":" + remote.ID()

	local, err := webrtc.NewTrackLocalStaticRTP(
		remote.Codec().RTPCodecCapability,
		id,
		participantID+":"+remote.StreamID(),
	)
	if err != nil {
		return nil, fmt.Errorf("create local RTP track: %w", err)
	}

	return &PublishedTrack{
		ID:                 id,
		OwnerParticipantID: participantID,
		MeetingCode:        meetingCode,
		Remote:             remote,
		Local:              local,
		stop:               make(chan struct{}),
	}, nil
}

func (track *PublishedTrack) Stop() {
	track.stopOnce.Do(func() {
		close(track.stop)
	})
}

func (track *PublishedTrack) ForwardRTP() error {
	for {
		select {
		case <-track.stop:
			return nil
		default:
		}

		packet, _, err := track.Remote.ReadRTP()
		if err != nil {
			return fmt.Errorf("read remote RTP: %w", err)
		}

		if err := track.Local.WriteRTP(packet); err != nil {
			return fmt.Errorf("write outgoing RTP: %w", err)
		}
	}
}
