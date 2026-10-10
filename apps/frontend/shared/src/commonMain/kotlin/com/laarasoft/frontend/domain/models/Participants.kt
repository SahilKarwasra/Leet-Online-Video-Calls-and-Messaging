package com.laarasoft.frontend.domain.models

 data class Participant(
     val id: String,
     val name: String,
     val isLocal: Boolean = false,
     val isMuted: Boolean = false,
     val isVideoOn: Boolean = false,
     val isSpeaking: Boolean = false,
     val isHandRaised: Boolean = false,
 )