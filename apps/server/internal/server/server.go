package server

import (
	"context"
	"errors"
	"net/http"
	"time"
)

type Server struct{ http *http.Server }

func New(addr string, h http.Handler) *Server {
	return &Server{http: &http.Server{
		Addr: addr, Handler: h, ReadHeaderTimeout: 5 * time.Second,
	}}
}

func (s *Server) Run() error {
	if err := s.http.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

func (s *Server) Shutdown(ctx context.Context) error { return s.http.Shutdown(ctx) }
