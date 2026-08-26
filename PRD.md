# Product Requirements Document (PRD)

## Product Name
Remote OBS Controller (Android)

## Version
v0.1 (Draft)

## Date
2026-05-22

## Author
Project Team

## 1. Purpose
Build an Android mobile app that remotely controls OBS scene switching over obs-websocket, with a two-step tap workflow:
- First tap on a scene sends it to Preview.
- Second tap on the same scene sends it to Program.

The app should mimic the operator behavior shown in the reference UI and provide simple, fast control for live production.

## 2. Problem Statement
OBS users operating from a distance (mobile workflow) need a lightweight control surface to stage scenes in Preview before taking them live in Program. Existing workflows are often desktop-bound or too complex for quick scene switching.

## 3. Goals
- Allow remote connection from Android phone to OBS.
- Display Preview and Program states clearly.
- Provide dynamic scene buttons sourced from OBS.
- Enforce tap behavior:
  - Tap once -> set Preview.
  - Tap same scene again -> set Program.
- Keep interactions low-latency and reliable on local network.

## 4. Non-Goals (v1)
- iOS support.
- Cloud relay / internet routing.
- Editing OBS scene collections.
- Video preview thumbnails from camera sources.
- Multi-user permissions.
- Dedicated sound feedback.

## 5. Target Users
- Streamers and event operators using OBS Studio.
- Solo creators needing mobile scene control.
- Production assistants handling simple switching workflows.

## 6. User Stories
1. As an OBS operator, I want to connect my Android phone to OBS so I can control scenes remotely.
2. As an operator, I want to tap a scene once to queue it in Preview.
3. As an operator, I want to tap the same scene again to take it live in Program.
4. As an operator, I want to see which scene is in Preview and Program at all times.
5. As an operator, I want clear connection feedback so I know whether commands are being sent.

## 7. Functional Requirements

### 7.1 Connection and Session
- FR-1: App must allow user to input OBS host/IP.
- FR-2: App must allow user to input OBS websocket port (default 4455).
- FR-3: App must allow user to input password.
- FR-4: App must support connect and disconnect actions.
- FR-5: App must show connection state: Disconnected, Connecting, Connected, Error.
- FR-5a: App must auto-connect to the last successful OBS server when possible.
- FR-5b: App must attempt silent auto-reconnect up to 5 times after an unexpected disconnect.
- FR-5c: App must show snackbar/toast messages when reconnecting.

### 7.2 Scene Controls
- FR-6: App must fetch scene names dynamically from OBS scene list and render them as tappable scene buttons.
- FR-7: First tap on a scene must send SetCurrentPreviewScene for that scene.
- FR-8: If the same scene is tapped again (without choosing another scene in between), app must send SetCurrentProgramScene.
- FR-9: If a different scene is tapped after first tap, that new scene becomes Preview target (and the two-step cycle restarts for that scene).
- FR-9a: App must use only the second-tap model for taking scenes live, with no dedicated Cut button in v1.
- FR-9b: App must keep scene ordering customizable through a rearrange flow in the menu screen.
- FR-9c: App must allow pinning selected scenes to a preferred top order in the rearrange flow.
- FR-9d: Scene list must update instantly when OBS scene names or order changes externally.

### 7.3 State Display
- FR-10: App must display current Preview scene name.
- FR-11: App must display current Program scene name.
- FR-12: Scene button visual state should indicate:
  - Preview scene: green border.
  - Program scene: red border.
- FR-13: If scene is neither preview nor program, show neutral border.

### 7.4 OBS Event Sync
- FR-14: App must listen to OBS websocket events for preview/program scene changes.
- FR-15: UI must update automatically when scenes are changed from outside the app (for example, via desktop OBS controls).

### 7.5 Failure Handling
- FR-16: On authentication failure or request failure, app must show a top-of-screen popup with a clear error.
- FR-17: On socket disconnect, app must revert connection status and prevent command attempts.
- FR-18: App should not crash on malformed or unexpected websocket messages.
- FR-18a: Error popup must include a copy button that copies the error message.

### 7.6 Menu Screen
- FR-19: App must support a menu screen accessible by dragging from the right edge of the main screen.
- FR-20: Menu screen must include server configuration, scene refresh, connection test, logging, accessibility, and lock options.
- FR-21: Menu screen must include a "Rearrange scenes" action for custom scene order and pinned scenes.
- FR-22: Menu screen must expose OBS transition options, including transition selection and duration.
- FR-23: Menu screen must include a toggle for haptic feedback on successful Program take.
- FR-24: Menu screen must include a setting to keep the screen awake while connected.
- FR-25: Menu screen must include a connection test screen for troubleshooting.
- FR-26: Menu screen must include an in-app log view for connection and message errors.
- FR-27: Menu screen must include an operator lock mode using a PIN or equivalent protection.
- FR-28: Menu screen must include an accessibility option for larger text/buttons.

### 7.7 Device and Feedback
- FR-29: App must persist last-used host, port, password, scene order, transition settings, haptic preference, and other menu settings between app launches.
- FR-30: App must operate in landscape orientation only.
- FR-31: App must adapt its landscape layout for phone and tablet form factors while keeping the same mockup-driven visual structure.
- FR-32: App must trigger haptic feedback on successful take to Program when the toggle is enabled.

## 8. UX Requirements
- Dark control-room style interface.
- Main screen must match the reference mockup layout closely.
- Top row has two panels: Preview and Program.
- Lower grid contains scene buttons mirroring reference layout.
- Right-edge drag gesture must open the menu screen.
- Status text should be readable at a glance.
- Touch targets must be finger-friendly (minimum 44dp recommended).
- Scene tiles should remain text-only permanently.
- Popup errors should appear at the top of the screen.
- Success and reconnect updates should use lightweight toast/snackbar messaging.

## 9. Technical Requirements
- Platform: Android (compatible with OBS 32.1.2 test environment; min SDK TBD, recommended Android 8.0+).
- Language: Kotlin.
- Networking: WebSocket client compatible with obs-websocket v5 protocol.
- Storage: Local persistent storage for user settings.
- Protocol operations required:
  - Identify/auth handshake.
  - Request: GetSceneList.
  - Request: SetCurrentPreviewScene.
  - Request: SetCurrentProgramScene.
  - Request: SetCurrentSceneTransition.
  - Events: CurrentPreviewSceneChanged, CurrentProgramSceneChanged.
  - Events: SceneListChanged or equivalent scene update events when available.

## 9.1 Implementation Notes
- Preview is implemented with periodic `GetSourceScreenshot` polling for the current preview scene.
- Program is implemented with WHEP playback when a WHEP URL is configured.
- If WHEP is not configured, Program falls back to `GetSourceScreenshot` polling.
- Screenshot polling is throttled so only one request per panel is in flight at a time.
- The app uses OBS websocket events to keep preview/program labels synchronized with external changes.
- The current build keeps the screenshot path separate from the control path so scene switching remains responsive.

## 10. Security and Privacy
- Password must not be logged in plaintext.
- Use local Wi-Fi only for v1; if remote internet control is introduced later, require TLS (wss) and hardening.
- App stores user settings locally for convenience, using plain local storage as requested.

## 11. Performance Requirements
- Command round-trip should feel immediate on local LAN.
- UI should remain responsive during reconnect attempts.
- Reconnect flow should complete within 5 seconds on healthy LAN.
- Tap-to-command response target is under 50 ms on a 50 Mbps Wi-Fi connection.
- Scene and state feeds should update in real time with little to no perceptible delay.

## 12. Acceptance Criteria
1. User can connect to OBS with valid host, port, and password.
2. App auto-connects to the last successful OBS server when possible.
3. Tapping a scene once sets Preview to that scene.
4. Tapping the same scene again sets Program to that scene.
5. Tapping a different scene after the first tap resets the second-tap cycle to the new scene.
6. Scene buttons are populated from OBS dynamically after connection.
7. Scene order can be rearranged and pinned from the menu screen.
8. Preview and Program labels update when changed externally in OBS.
9. Menu screen exposes server settings, refresh, connection test, logs, accessibility, lock mode, transitions, and haptic toggle.
10. Connection and error states are visible and understandable.
11. Error popup appears at the top of the screen and includes a copy button.
12. The app retries reconnecting silently up to 5 times and shows reconnect snackbars/toasts.
13. Last-used connection and menu settings persist after app restart.
14. App runs in landscape only.
15. App adapts to phone and tablet sizes without changing the core mockup layout.
16. Successful take to Program triggers haptic feedback when enabled.
17. Connection test and logging tools are available from the menu screen.
18. App stays within local Wi-Fi usage assumptions and does not depend on cloud services.

## 13. Milestones
- M1: Finalize PRD and UI wireframe agreement.
- M2: Build Android prototype with dynamic scene rendering, right-edge menu drawer, and layout matching the reference.
- M3: Integrate obs-websocket authentication, scene list fetch, and scene switching logic.
- M4: Add auto-reconnect, event sync, error popups, and logging.
- M5: Add persistence, rearrange scenes, transitions, haptics, accessibility, and field test on real OBS setup.

## 14. Decisions (Resolved)
1. Scene names will be fetched dynamically from OBS.
2. Program transition will use only the second-tap model (no dedicated Cut button).
3. Last-used connection settings and menu preferences will be persisted locally.
4. App will be landscape only.
5. Haptic feedback will be available on successful take, with no sound confirmation.
6. The menu screen will be opened from the right edge drag gesture.
7. Error handling will use top-screen popups with copy support.

## 15. Out of Scope Risks / Dependencies
- Requires OBS Studio with obs-websocket enabled.
- Requires same network connectivity and firewall allowance.
- Dynamic scene lists depend on successful OBS scene-list retrieval.
- Custom transitions depend on the transitions exposed by OBS in the current profile.

## 16. Current Build Summary
- The app currently includes the control flow, scene sync, live preview/program panels, menu settings, reconnect behavior, and local persistence described above.
- Live feed quality depends on OBS source availability, LAN latency, and the optional WHEP stream path for Program.
