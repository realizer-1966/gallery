// Voice Chat — WebRTC signaling server.
//
// Role: relay SDP/ICE between a browser peer and the Android phone peer,
// and serve the browser client UI (public/).
//
// Protocol (JSON over WebSocket):
//   client -> server: { "type": "join", "room": "<room>", "role": "browser"|"phone" }
//   server -> client: { "type": "ready" }                          // both peers present
//   client -> server: { "type": "sdp",  "sdp": {...} }             // forward to the other peer
//   client -> server: { "type": "ice",  "candidate": {...} }
//   server -> client: { "type": "peer-left" }
//
// The server never inspects SDP/ICE payloads — it is a dumb relay.
// WebRTC media (SRTP) and the TTS data channel (DTLS) are end-to-end encrypted
// between the browser and the phone; the server cannot read them.

"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 8080;
const HOST = process.env.HOST || "0.0.0.0";

// ---------------------------------------------------------------------------
// Static file server (serves the browser client)
// ---------------------------------------------------------------------------

const PUBLIC_DIR = path.join(__dirname, "public");
const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
};

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent(new URL(req.url, `http://${req.headers.host}`).pathname);
  let filePath = path.normalize(path.join(PUBLIC_DIR, urlPath === "/" ? "index.html" : urlPath));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403).end("Forbidden");
    return;
  }
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain" }).end("Not found");
      return;
    }
    res.writeHead(200, { "Content-Type": MIME[path.extname(filePath)] || "application/octet-stream" });
    res.end(data);
  });
});

// ---------------------------------------------------------------------------
// WebSocket signaling
// ---------------------------------------------------------------------------

const wss = new WebSocketServer({ server, path: "/ws" });

/** @type {Map<string, {browser: WebSocket|null, phone: WebSocket|null}>} */
const rooms = new Map();

function otherPeer(room, role) {
  return role === "browser" ? room.phone : room.browser;
}

function send(ws, obj) {
  if (ws && ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

wss.on("connection", (ws) => {
  let roomId = null;
  let role = null;

  ws.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (msg.type === "join") {
      roomId = String(msg.room || "").trim().toLowerCase();
      role = msg.role === "phone" ? "phone" : "browser";
      if (!roomId) {
        send(ws, { type: "error", message: "Missing room id" });
        return;
      }
      let room = rooms.get(roomId);
      if (!room) {
        room = { browser: null, phone: null };
        rooms.set(roomId, room);
      }
      // Replacing a stale peer of the same role (e.g. reconnects).
      const prev = room[role];
      if (prev && prev !== ws && prev.readyState === prev.OPEN) {
        prev.close();
      }
      room[role] = ws;
      ws.on("close", () => {
        if (rooms.get(roomId)?.[role] === ws) {
          rooms.get(roomId)[role] = null;
          const other = otherPeer(room, role);
          if (other && other.readyState === other.OPEN) {
            send(other, { type: "peer-left" });
          }
          if (!room.browser && !room.phone) {
            rooms.delete(roomId);
          }
        }
      });
      send(ws, { type: "joined", room: roomId, role });
      const other = otherPeer(room, role);
      if (other && other.readyState === other.OPEN) {
        send(ws, { type: "ready" });
        send(other, { type: "ready" });
      }
      return;
    }

    // All other message types are relayed to the peer in the same room.
    if (!roomId) return;
    const room = rooms.get(roomId);
    if (!room) return;
    const target = otherPeer(room, role);
    if (target && target.readyState === target.OPEN) {
      // Add the originating role so the receiver knows who sent it.
      msg.from = role;
      target.send(JSON.stringify(msg));
    }
  });
});

server.listen(PORT, HOST, () => {
  console.log(`Voice Chat signaling server listening on http://${HOST}:${PORT}`);
  console.log(`Browser client:  http://<this-host>:${PORT}/`);
  console.log(`WebSocket:       ws://<this-host>:${PORT}/ws`);
});
