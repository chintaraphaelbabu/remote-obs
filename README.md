# Remote OBS

Android app to remote-control OBS scenes over Wi-Fi.

- Two-tap: tap once Preview, tap again Program
- **Program**: real-time 1080p 30fps via WebRTC (screenshots if relay not running)
- **Preview**: screenshot-based (always works, no extra setup)
- mDNS auto-discovery — phone finds OBS machine automatically
- Scene rearrange, transitions, PIN lock, logs, haptics

## Setup

### 1. OBS (one-time)

- Tools → WebSocket Server Settings → **Enable**, set a password (port 4455)
- Tools → Virtual Camera → Start, Output Type: **Program**

### 2. Relay (starts with OBS)

**Option A — double-click (simplest):**
Double-click `Start Remote OBS Relay.bat` after OBS is open.

**Option B — automatic with OBS (for volunteers):**
1. Copy `companion\` folder somewhere permanent (e.g. `C:\obs-relay\`)
2. OBS → Tools → Scripts → + → add `companion\obs-relay.lua`
3. Relay starts automatically when OBS opens

### 3. Phone

- Install the APK
- mDNS auto-fills host — enter password and WHEP URL (shown in relay window)
- Tap scenes to switch

## Notes

- YouTube stream is untouched — relay uses Virtual Camera, not the stream output
- Both devices on same Wi-Fi
- Preview stays screenshot-based (OBS only outputs one video feed)
"# remote-obs" 
