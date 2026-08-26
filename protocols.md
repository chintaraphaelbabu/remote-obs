# Protocols

This document explains how the app talks to OBS and how the preview/program experience is assembled from websocket requests, websocket events, and panel rendering.

## Overview

The app uses three control layers:

1. obs-websocket requests for scene control and data fetches.
2. obs-websocket events for live state synchronization.
3. UI polling/rendering for the live feed panels.

The goal is to keep the operator workflow simple:

1. Tap once to stage a scene in Preview.
2. Tap again to take the same scene to Program.
3. Keep the screen showing current preview/program state without manual refresh.

## Connection flow

1. The app opens a websocket to `ws://<host>:<port>`.
2. OBS sends a Hello message.
3. If OBS requires authentication, the app computes the auth token from the configured password, the server challenge, and the salt.
4. The app sends Identify with `rpcVersion` 1 and the auth token when required.
5. After OBS identifies the session, the app marks itself connected and starts data sync.

If the socket closes unexpectedly, the app:

1. Reverts to a disconnected state.
2. Optionally starts silent reconnect attempts when auto-connect is enabled.
3. Shows reconnect messages through snackbar/toast events.

## Scene state synchronization

The app listens for these OBS events:

1. `CurrentPreviewSceneChanged`
2. `CurrentProgramSceneChanged`

When these events arrive, the app updates its internal state immediately so the scene buttons and labels stay accurate even if the user changes scenes from desktop OBS or another controller.

The app also fetches scene information on demand with `GetSceneList` so it can rebuild the scene grid and recover the current preview/program values after connecting or refreshing.

## Two-step scene control

The scene tap behavior is intentionally stateful.

1. If the tapped scene is not the current pending take scene, the app sends `SetCurrentPreviewScene`.
2. If the same scene is tapped again before another scene is chosen, the app sends `SetCurrentProgramScene`.
3. If a different scene is tapped, that scene becomes the new preview target and the cycle restarts.

This mirrors the operator flow in the PRD and avoids a separate Cut button.

## Live feed panels

### Preview panel

The Preview panel is screenshot based.

1. The app reads the current preview scene name.
2. It sends `GetSourceScreenshot` for that scene.
3. OBS returns base64-encoded image data.
4. The app decodes the image and draws it into the Preview panel.
5. The polling loop repeats while connected.

The polling loop is throttled so there is only one in-flight screenshot request per panel at a time. That prevents stale screenshot requests from piling up when the network or OBS is slow.

### Multiview panel

The right monitor panel has two modes.

1. If a Multiview WHEP URL is configured, the app renders the 30 FPS OBS Multiview projector feed.
2. If no WHEP URL is configured, the app falls back to the current Program screenshot.

When WHEP is active, the app stops Program and per-scene screenshot polling to avoid competing traffic and unnecessary decoding work.

The relay opens a separate windowed OBS Multiview projector and captures it with FFmpeg. This preserves OBS Virtual Camera for Zoom and other applications.

## Screenshot request details

Screenshot fetches use `GetSourceScreenshot` with compact image settings so the UI stays responsive.

The request is configured to:

1. Ask for JPG output.
2. Use a 720p render size.
3. Use a lower compression quality so the payload stays smaller than a full-quality frame.

The response image data is base64 encoded. The app strips any data URI prefix, decodes the payload, and turns it into a bitmap for Compose.

## Latency strategy on low-end ARM tablets

The app is running on a Samsung SM-T295 class device with a Cortex-A53 CPU, so the main bottlenecks are websocket round-trip overlap, base64 decode cost, bitmap churn, and main-thread draw contention. The goal is to keep the operator-facing latency under 50 ms while holding the feed at 720p and keeping the payload smaller through compression.

### 1. Adaptive polling with backpressure protection

If a screenshot round-trip takes longer than 40 ms, the next poll tick should be skipped instead of starting another request.

Why this helps on a low-end ARM device:

1. It prevents the app from building a request queue that is already stale by the time frames arrive.
2. It avoids wasting CPU on work that cannot complete before the next tick.
3. It keeps the websocket pipeline from saturating when OBS or Wi-Fi momentarily slows down.

Visible artifact risk:

1. The panel may hold the previous frame for one extra tick during a brief slowdown.
2. If the device is under heavy load, the feed can look slightly less frequent, even though it remains current.

### 2. Single shared polling coroutine with staggered panel ticks

Preview and Program should share one polling scheduler rather than running two independent loops.
Each panel should be polled on alternating half-ticks.

Why this helps on a low-end ARM device:

1. It reduces peak websocket pressure by avoiding two simultaneous request bursts.
2. It spreads CPU work across the tick window instead of forcing both panels to compete at the same instant.
3. It lowers contention on the local network stack and the app's request parsing path.

Visible artifact risk:

1. The two panels will no longer update on exactly the same timestamp, so there can be a small phase offset.
2. If the tick interval is too large, the stagger becomes noticeable as alternating panel updates.

### 3. Strict background decode and prefix stripping

Base64 stripping and bitmap decoding must happen on a background thread only.
The main thread should receive only the final bitmap reference or a lightweight state update.

Why this helps on a low-end ARM device:

1. Base64 decode is CPU-bound and expensive on Cortex-A53 cores.
2. Keeping decode off the UI thread prevents jank in Compose recomposition and touch handling.
3. It reduces the chance that a decoded frame misses the next display deadline.

Visible artifact risk:

1. If decode work is slower than expected, the app may reuse the previous frame for a little longer.
2. Bad background-thread error handling can leave the panel blank, so the last-good-frame cache matters.

### 4. Vsync-aligned bitmap publication

Decoded bitmaps should be posted to the UI on the next display frame boundary rather than immediately when decoding completes.
In Compose, this should be done with the frame clock equivalent so the bitmap swap lands cleanly on vsync.

Why this helps on a low-end ARM device:

1. It avoids mid-frame bitmap swaps that can produce visible tearing or micro-stutter.
2. It lets the UI batch work with the display refresh cadence instead of fighting it.
3. It improves perceived smoothness even when actual screenshot RTT is unchanged.

Visible artifact risk:

1. There can be a one-frame delay between decode completion and render.
2. If overused, vsync alignment can make the feed feel slightly more latent even though it looks smoother.

For a live cut, the main visible artifact to watch is a frame that lands one refresh late if decode finishes just after the frame boundary.

### 5. Last-good-bitmap caching during in-flight gaps

Keep the last successful bitmap on screen until the next decoded frame is ready.
Do not clear the panel to blank while a new screenshot is in flight.

Why this helps on a low-end ARM device:

1. It removes flicker caused by short decode or network gaps.
2. It makes latency spikes less obvious because the operator always sees a stable image.
3. It protects against brief stalls that are common on a budget ARM tablet under load.

Visible artifact risk:

1. The panel may briefly show a slightly stale frame during a slow round-trip.
2. During a live cut, a cached frame can hide that the source is still settling, so the app should only swap to a new frame when it is ready and valid.

### Practical ordering

The lowest-risk order of application is:

1. Add backpressure protection.
2. Move to a shared staggered polling coroutine.
3. Ensure decode and prefix stripping run off the UI thread.
4. Publish bitmaps on vsync.
5. Preserve the last good bitmap until the next frame lands.

That order reduces queueing first, then reduces CPU contention, then improves perceived smoothness.

## Program take and feedback

When a `SetCurrentProgramScene` request succeeds:

1. The app clears the pending take state.
2. It emits a haptic success event when haptics are enabled.
3. It logs the successful take.

If the request fails, the error is surfaced through the app's error banner and log stream.

## Reconnect behavior

If the connection drops unexpectedly and auto-connect is enabled, the app attempts reconnects up to five times.

1. Each attempt updates the connection label.
2. A snackbar message announces the reconnect attempt.
3. After the final failure, the app settles back to disconnected.

Manual disconnect disables reconnect attempts and stops screenshot polling immediately.

## Persisted settings

The app stores the following locally:

1. Host and port.
2. Password.
3. Auto-connect and menu preferences.
4. Scene ordering and pinned scenes.
5. Transition selection and duration.
6. Optional WHEP URL.

## Practical implications

1. Preview is lightweight and always screenshot-driven.
2. Program is smoother when WHEP is available.
3. OBS scene changes from outside the app are reflected automatically through websocket events.
4. Scene list refreshes are request-based rather than continuously streamed.
5. The app is designed for local network use rather than cloud routing.
