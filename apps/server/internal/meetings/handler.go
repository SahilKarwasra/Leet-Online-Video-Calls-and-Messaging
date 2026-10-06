package meetings

import (
	"errors"
	"net/http"

	"github.com/gin-gonic/gin"
)

type Handler struct{ svc *Service }

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func (h *Handler) Create(c *gin.Context) {
	var req CreateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	m, err := h.svc.Create(c.Request.Context(), req.HostName)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "could not create meeting"})
		return
	}
	c.JSON(http.StatusCreated, CreateResponse{Meeting: toResponse(m), HostToken: m.HostToken})
}

func (h *Handler) Get(c *gin.Context) {
	m, err := h.svc.GetByCode(c.Request.Context(), c.Param("code"))
	if errors.Is(err, ErrNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "meeting not found"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "internal error"})
		return
	}
	c.JSON(http.StatusOK, toResponse(m))
}
