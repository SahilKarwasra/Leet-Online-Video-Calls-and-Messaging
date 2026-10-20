// Minimal browser client for the Leet SFU backend. Same protocol as the Kotlin apps:
// join over WebSocket, send one offer, answer every offer the server sends back.
const HOST = `${location.hostname || "localhost"}:8080`;
const $ = (id) => document.getElementById(id);

let ws, pc, localStream;
let names = {};              // participantId -> name
let pendingCandidates = [];  // remote ICE that arrived before a remote description
let offerSent = false;
let localCandidates = [];    // our ICE gathered before the offer went out
let myId = null;
let handRaised = false;
let micOn = true;
let cameraWanted = true;          // what the user asked for; screen sharing doesn't change it
let cameraTrack = null;           // released (stopped) while the camera is off
let screenTrack = null;
let videoSender = null;
// Per tile: hand, audio, video, screen. State can arrive before the
// person's tile exists, so it's kept here and applied when the tile is created.
const tileState = {};
const REACTIONS = ["👍", "👏", "❤️", "😂", "🎉", "😮"];

$("join").onclick = async () => {
  try {
    let code = $("code").value.trim();
    if (!code) code = await createMeeting();
    await joinMeeting(code, $("name").value.trim() || "Web");
  } catch (e) {
    $("status").textContent = "Error: " + e.message;
  }
};

async function createMeeting() {
  const res = await fetch(`http://${HOST}/api/v1/meeting`, { method: "POST" });
  const body = await res.json();
  return body.data.code;
}

async function joinMeeting(code, name) {
  localStream = await navigator.mediaDevices.getUserMedia({
    audio: true,
    video: { width: 640, height: 480 },
  });
  cameraTrack = localStream.getVideoTracks()[0] || null;
  addTile("local", "You", new MediaStream(localStream.getVideoTracks()), true);

  $("lobby").hidden = true;
  $("call").hidden = false;
  $("meeting-code").textContent = code;

  ws = new WebSocket(`ws://${HOST}/ws/meetings/${code}`);
  ws.onopen = () => send("meeting.join", { name });
  ws.onmessage = (e) => handleEvent(JSON.parse(e.data));
  ws.onclose = () => console.log("WebSocket closed");
}

function send(type, data) {
  ws.send(JSON.stringify({ type, data }));
}

async function handleEvent({ type, data }) {
  console.log("<-", type, data);
  switch (type) {
    case "meeting.joined":
      myId = data.participant_id;
      await startCall();
      break;
    case "meeting.already_exists_participants":
      (data || []).forEach((p) => (names[p.participant_id] = p.name));
      break;
    case "meeting.participant_joined":
      names[data.participant_id] = data.name;
      break;
    case "meeting.left":
      document.getElementById("tile-" + data.participant_id)?.remove();
      delete tileState[data.participant_id];
      break;
    case "chat.message":
      addMessage(data);
      break;
    case "meeting.reaction":
      floatReaction(data.emoji, data.name);
      break;
    case "meeting.hand_raise":
      updateTile(data.participant_id, { hand: data.raised });
      break;
    case "meeting.media_state":
      updateTile(data.participant_id, { audio: data.audio, video: data.video, screen: data.screen });
      break;
    case "webrtc.offer":
      await pc.setRemoteDescription({ type: "offer", sdp: data.sdp });
      await flushRemoteCandidates();
      await pc.setLocalDescription(await pc.createAnswer());
      send("webrtc.answer", { type: "answer", sdp: pc.localDescription.sdp });
      break;
    case "webrtc.answer":
      await pc.setRemoteDescription({ type: "answer", sdp: data.sdp });
      await flushRemoteCandidates();
      break;
    case "webrtc.ice_candidate":
      if (pc.remoteDescription) await pc.addIceCandidate(data);
      else pendingCandidates.push(data);
      break;
    case "error":
      console.warn("Server error:", data);
      break;
  }
}

async function startCall() {
  pc = new RTCPeerConnection({ iceServers: [{ urls: "stun:stun.l.google.com:19302" }] });
  localStream.getTracks().forEach((track) => {
    const sender = pc.addTrack(track, localStream);
    if (track.kind === "video") videoSender = sender;
  });

  pc.onicecandidate = ({ candidate }) => {
    if (!candidate) return;
    // The server rejects candidates that arrive before our offer.
    if (offerSent) send("webrtc.ice_candidate", candidate.toJSON());
    else localCandidates.push(candidate.toJSON());
  };

  // The SFU names streams "<participantId>:<streamId>".
  pc.ontrack = ({ track, streams }) => {
    const participantId = (streams[0]?.id || track.id).split(":")[0];
    const tile = document.getElementById("tile-" + participantId);
    if (tile) tile.querySelector("video").srcObject.addTrack(track);
    else addTile(participantId, names[participantId] || "Guest", new MediaStream([track]), false);
  };

  pc.onconnectionstatechange = () => console.log("Connection:", pc.connectionState);

  await pc.setLocalDescription(await pc.createOffer());
  send("webrtc.offer", { type: "offer", sdp: pc.localDescription.sdp });
  offerSent = true;
  localCandidates.forEach((c) => send("webrtc.ice_candidate", c));
  localCandidates = [];
  publishMediaState();
}

// Tell everyone what we're sending, so they show our avatar instead of a frozen frame.
function publishMediaState() {
  const state = { audio: micOn, video: !!cameraTrack && !screenTrack, screen: !!screenTrack };
  if (ws?.readyState === WebSocket.OPEN) send("meeting.media_state", state);
  updateTile("local", state);
}

// The camera is released when off (its light goes off) and reopened when on.
async function setCamera(on) {
  cameraWanted = on;
  if (!screenTrack) {
    if (on && !cameraTrack) {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 } });
      cameraTrack = stream.getVideoTracks()[0];
      await videoSender?.replaceTrack(cameraTrack);
      setLocalPreview(cameraTrack);
    } else if (!on && cameraTrack) {
      await videoSender?.replaceTrack(null);
      cameraTrack.stop();
      cameraTrack = null;
    }
  }
  $("cam").textContent = cameraWanted ? "Camera off" : "Camera on";
  publishMediaState();
}

async function startScreenShare() {
  const stream = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: 15 } });
  screenTrack = stream.getVideoTracks()[0];
  screenTrack.onended = stopScreenShare; // the browser's own "Stop sharing" button
  await videoSender?.replaceTrack(screenTrack);
  cameraTrack?.stop();
  cameraTrack = null;
  $("share").textContent = "Stop presenting";
  publishMediaState();
}

async function stopScreenShare() {
  if (!screenTrack) return;
  const track = screenTrack;
  screenTrack = null;
  track.stop();
  $("share").textContent = "Share screen";
  if (cameraWanted) {
    await setCamera(true);
  } else {
    await videoSender?.replaceTrack(null);
    publishMediaState();
  }
}

function setLocalPreview(track) {
  const video = document.querySelector("#tile-local video");
  if (video) video.srcObject = new MediaStream(track ? [track] : []);
}

async function flushRemoteCandidates() {
  for (const c of pendingCandidates) await pc.addIceCandidate(c);
  pendingCandidates = [];
}

function addTile(id, label, stream, isLocal) {
  const tile = document.createElement("div");
  tile.className = "tile" + (isLocal ? " local" : "");
  tile.id = "tile-" + id;

  const video = document.createElement("video");
  video.autoplay = true;
  video.playsInline = true;
  video.muted = isLocal; // never play your own mic back
  video.srcObject = stream;

  // Shown instead of the video while the camera is off.
  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.textContent = initials(label);

  const name = document.createElement("span");
  name.dataset.label = label;

  const hand = document.createElement("b");
  hand.className = "hand";
  hand.textContent = "✋";

  tile.append(video, avatar, name, hand);
  $("grid").append(tile);
  renderTile(id);
}

function initials(name) {
  return name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() || "").join("") || "?";
}

function updateTile(id, changes) {
  tileState[id] = { ...(tileState[id] || {}), ...changes };
  renderTile(id);
}

function renderTile(id) {
  const tile = document.getElementById("tile-" + id);
  if (!tile) return;
  const s = { audio: true, video: true, screen: false, hand: false, ...tileState[id] };
  tile.classList.toggle("video-off", !s.video && !s.screen);
  tile.classList.toggle("screen", s.screen);
  tile.querySelector(".hand").hidden = !s.hand;
  const name = tile.querySelector("span");
  name.textContent = (s.audio ? "" : "🔇 ") + name.dataset.label + (s.screen ? " (presenting)" : "");
}

function addMessage({ participant_id, name, message }) {
  const el = document.createElement("p");
  el.className = participant_id === myId ? "mine" : "";
  const who = document.createElement("b");
  who.textContent = participant_id === myId ? "You" : name;
  el.append(who, document.createTextNode(message)); // textContent only: never render chat as HTML
  $("messages").append(el);
  $("messages").scrollTop = $("messages").scrollHeight;
}

function floatReaction(emoji, name) {
  const el = document.createElement("div");
  el.className = "float";
  el.style.left = 20 + Math.random() * 60 + "%";
  el.textContent = `${emoji} ${name}`;
  $("floating").append(el);
  setTimeout(() => el.remove(), 2800);
}

$("chat-form").onsubmit = (e) => {
  e.preventDefault();
  const message = $("chat-input").value.trim();
  if (!message) return;
  send("chat.message", { message }); // shown when the server echoes it back
  $("chat-input").value = "";
};

REACTIONS.forEach((emoji) => {
  const button = document.createElement("button");
  button.textContent = emoji;
  button.onclick = () => {
    send("meeting.reaction", { emoji });
    floatReaction(emoji, "You"); // the server doesn't echo reactions to the sender
  };
  $("reactions").append(button);
});

$("hand").onclick = () => {
  handRaised = !handRaised;
  send("meeting.hand_raise", { raised: handRaised });
  updateTile("local", { hand: handRaised });
  $("hand").textContent = handRaised ? "Lower hand" : "Raise hand";
};

$("copy").onclick = async () => {
  await navigator.clipboard.writeText($("meeting-code").textContent);
  $("copy").textContent = "Copied";
  setTimeout(() => ($("copy").textContent = "Copy"), 2000);
};

$("mic").onclick = () => {
  micOn = !micOn;
  localStream.getAudioTracks().forEach((t) => (t.enabled = micOn));
  $("mic").textContent = micOn ? "Mute" : "Unmute";
  publishMediaState();
};

$("cam").onclick = () => setCamera(!cameraWanted).catch((e) => console.warn("Camera:", e));

$("share").onclick = () =>
  (screenTrack ? stopScreenShare() : startScreenShare()).catch((e) => console.warn("Share:", e));

$("leave").onclick = () => {
  pc?.close();
  ws?.close();
  localStream?.getTracks().forEach((t) => t.stop());
  cameraTrack?.stop();
  screenTrack?.stop();
  location.reload();
};
