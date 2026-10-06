package meetings

import "time"

type Status string

const (
	StatusActive Status = "active"
	StatusEnded  Status = "ended"
)

type Meeting struct {
	ID          string
	Code        string
	HostToken   string
	HostName    string
	Status      Status
	LiveKitRoom string
	CreatedAt   time.Time
}
