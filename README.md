# Remote OBS

Android app to remote-control OBS scenes over Wi-Fi.

- Two-tap: tap once Preview, tap again Program
- **Multiview**: real-time OBS projector feed via WebRTC
- **Preview**: screenshot-based (always works, no extra setup)
- mDNS auto-discovery — phone finds OBS machine automatically
- Scene rearrange, transitions, PIN lock, logs, haptics

## Setup

### 1. OBS (one-time)

- Tools → WebSocket Server Settings → **Enable**, set a password (port 4455)
- Tools → Virtual Camera → Start for Zoom; keep its output set to **Program**
- Add `companion\obs-relay.lua` under Tools → Scripts; it opens a separate **Multiview** projector for the relay

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

- Zoom and streaming outputs are untouched — relay captures the separate Multiview projector, not Virtual Camera
- Both devices on same Wi-Fi
- The app's right monitor is the live Multiview feed; scene buttons remain active for switching
"# remote-obs" 
