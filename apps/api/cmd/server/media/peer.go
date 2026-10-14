package media

import "github.com/pion/webrtc/v4"

type Peer struct {
	ParticipantId  string
	MeetingCode    string
	PeerConnection *webrtc.PeerConnection
}

func NewPeer(participantId string, meetingCode string) (*Peer, error) {
	api := webrtc.NewAPI()

	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{
				URLs: []string{
					"stun:stun.l.google.com:19302",
				},
			},
		},
	}

	peerConnection, err := api.NewPeerConnection(config)
	if err != nil {
		return nil, err
	}
	peer := &Peer{
		ParticipantId:  participantId,
		MeetingCode:    meetingCode,
		PeerConnection: peerConnection,
	}
	return peer, nil
}

func (peer *Peer) Close() error {
	return peer.PeerConnection.Close()
}
