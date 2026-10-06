package redis

import "fmt"

func MeetingKey(id string) string        { return "meeting:" + id }
func MeetingCodeKey(code string) string  { return "meeting:code:" + code }
func ParticipantsKey(id string) string   { return fmt.Sprintf("meeting:%s:participants", id) }
func PresenceKey(mid, pid string) string { return fmt.Sprintf("presence:%s:%s", mid, pid) }
func ChatKey(id string) string           { return "chat:" + id }
func EventsChannel(id string) string     { return fmt.Sprintf("meeting:%s:events", id) }
