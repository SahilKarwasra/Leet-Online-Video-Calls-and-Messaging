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
const raisedHands = new Set(); // participantIds; a hand can arrive before that person's tile exists
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
  addTile("local", "You", localStream, true);

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
      raisedHands.delete(data.participant_id);
      break;
    case "chat.message":
      addMessage(data);
      break;
    case "meeting.reaction":
      floatReaction(data.emoji, data.name);
      break;
    case "meeting.hand_raise":
      setTileHand(data.participant_id, data.raised);
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
  localStream.getTracks().forEach((track) => pc.addTrack(track, localStream));

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

  const name = document.createElement("span");
  name.textContent = label;

  const hand = document.createElement("b");
  hand.className = "hand";
  hand.textContent = "✋";
  hand.hidden = !raisedHands.has(id);

  tile.append(video, name, hand);
  $("grid").append(tile);
}

function setTileHand(participantId, raised) {
  if (raised) raisedHands.add(participantId);
  else raisedHands.delete(participantId);
  const hand = document.querySelector(`#tile-${participantId} .hand`);
  if (hand) hand.hidden = !raised;
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
  setTileHand("local", handRaised);
  $("hand").textContent = handRaised ? "Lower hand" : "Raise hand";
};

$("copy").onclick = async () => {
  await navigator.clipboard.writeText($("meeting-code").textContent);
  $("copy").textContent = "Copied";
  setTimeout(() => ($("copy").textContent = "Copy"), 2000);
};

$("mic").onclick = () => {
  const track = localStream.getAudioTracks()[0];
  track.enabled = !track.enabled;
  $("mic").textContent = track.enabled ? "Mute" : "Unmute";
};

$("cam").onclick = () => {
  const track = localStream.getVideoTracks()[0];
  track.enabled = !track.enabled;
  $("cam").textContent = track.enabled ? "Camera off" : "Camera on";
};

$("leave").onclick = () => {
  pc?.close();
  ws?.close();
  localStream?.getTracks().forEach((t) => t.stop());
  location.reload();
};
