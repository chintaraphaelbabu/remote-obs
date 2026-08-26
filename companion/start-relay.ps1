param(
    [string]$MediamtxDir = "$env:LOCALAPPDATA\mediamtx",
    [int]$Fps = 30
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# ─── helpers ────────────────────────────────────────────────────
function Ensure-Tool($name, $check, $install) {
    if (!(Get-Command $check -ErrorAction Ignore)) {
        Write-Host "Downloading $name..." -ForegroundColor Yellow
        Invoke-Expression $install
    }
}

function Die($msg) { Write-Host "`nERROR: $msg" -ForegroundColor Red; Read-Host "Press Enter"; exit 1 }

# ─── 1. ensure ffmpeg ──────────────────────────────────────────
Ensure-Tool "ffmpeg" "ffmpeg" {
    if (Get-Command winget -ErrorAction Ignore) {
        winget install --id Gyan.FFmpeg -e --silent --accept-package-agreements --accept-source-agreements
        $env:Path = [Environment]::GetEnvironmentVariable("Path", "User") + ";$env:Path"
    } else {
        Die "ffmpeg not found. Install from https://ffmpeg.org/download.html or via 'winget install Gyan.FFmpeg'"
    }
}

# ─── 2. ensure mediamtx ────────────────────────────────────────
$mtxExe = "$MediamtxDir\mediamtx.exe"
if (!(Test-Path $mtxExe)) {
    Write-Host "Downloading MediaMTX..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $MediamtxDir -Force | Out-Null
    $url = "https://github.com/bluenviron/mediamtx/releases/latest/download/mediamtx_win_amd64.zip"
    $zip = "$env:TEMP\mediamtx.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $MediamtxDir -Force
    Remove-Item $zip
}

# ─── 3. mediamtx config ────────────────────────────────────────
$cfg = "$MediamtxDir\mediamtx.yml"
if (!(Test-Path $cfg)) {
@"
rtmp: yes
rtmpPort: 1935
webrtc: yes
webrtcPorts: 8189:8199
"@ | Out-File -Encoding utf8 $cfg
}

# ─── 4. start mediamtx ─────────────────────────────────────────
Write-Host "Starting MediaMTX..." -ForegroundColor Green
$mtx = Start-Process -FilePath $mtxExe -WorkingDirectory $MediamtxDir -WindowStyle Hidden -PassThru
Start-Sleep 2
if ($mtx.HasExited) { Die "MediaMTX failed to start. Check $MediamtxDir\mediamtx.log" }

# ─── 5. start ffmpeg capture ───────────────────────────────────
$projector = "OBS Multiview"
Write-Host "Capturing '$projector' → RTMP :1935..." -ForegroundColor Green
Start-Sleep 3
$availableEncoders = ffmpeg -hide_banner -encoders 2>$null
$videoEncoder = "libx264"
$encoderArgs = @("-preset", "ultrafast", "-tune", "zerolatency")
if ($availableEncoders -match "h264_nvenc") {
    $videoEncoder = "h264_nvenc"
    $encoderArgs = @("-preset", "p1", "-tune", "ull", "-rc", "cbr")
} elseif ($availableEncoders -match "h264_amf") {
    $videoEncoder = "h264_amf"
    $encoderArgs = @("-quality", "speed", "-usage", "ultralowlatency", "-rc", "cbr")
} elseif ($availableEncoders -match "h264_qsv") {
    $videoEncoder = "h264_qsv"
    $encoderArgs = @("-preset", "veryfast", "-look_ahead", "0")
}
Write-Host "Using H.264 encoder '$videoEncoder' at $Fps FPS." -ForegroundColor Green
$ffmpegArgs = @(
    "-f", "gdigrab", "-framerate", "$Fps", "-draw_mouse", "0", "-thread_queue_size", "512", "-rtbufsize", "256M", "-i", "title=$projector"
    "-vf", "scale=1280:-2,fps=$Fps", "-fps_mode", "cfr"
    "-c:v", $videoEncoder
    $encoderArgs
    "-b:v", "4M", "-maxrate", "4M", "-bufsize", "2M"
    "-g", "$Fps", "-keyint_min", "$Fps", "-sc_threshold", "0"
    "-pix_fmt", "yuv420p", "-f", "flv", "rtmp://localhost:1935/live/multiview"
)
$ff = Start-Process -FilePath "ffmpeg" -ArgumentList $ffmpegArgs -WindowStyle Hidden -PassThru
Start-Sleep 1
if ($ff.HasExited -and $videoEncoder -ne "libx264") {
    Write-Host "Hardware encoder '$videoEncoder' unavailable; falling back to libx264." -ForegroundColor Yellow
    $ffmpegArgs = @(
        "-f", "gdigrab", "-framerate", "$Fps", "-draw_mouse", "0", "-thread_queue_size", "512", "-rtbufsize", "256M", "-i", "title=$projector"
        "-vf", "scale=1280:-2,fps=$Fps", "-fps_mode", "cfr"
        "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency"
        "-b:v", "4M", "-maxrate", "4M", "-bufsize", "2M"
        "-g", "$Fps", "-keyint_min", "$Fps", "-sc_threshold", "0"
        "-pix_fmt", "yuv420p", "-f", "flv", "rtmp://localhost:1935/live/multiview"
    )
    $ff = Start-Process -FilePath "ffmpeg" -ArgumentList $ffmpegArgs -WindowStyle Hidden -PassThru
    Start-Sleep 1
}
if ($ff.HasExited) { Die "ffmpeg failed. Is OBS running with the Multiview projector open?" }

# ─── 6. start mDNS ─────────────────────────────────────────────
Write-Host "Starting mDNS advertisement..." -ForegroundColor Green
$mdns = Start-Process -WindowStyle Hidden -FilePath "powershell" -ArgumentList @(
    "-NoProfile", "-Command",
    "pip install zeroconf -q 2>`$null; python '$PSScriptRoot\obs-mdns.py'"
) -PassThru

# ─── 7. show info ──────────────────────────────────────────────
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.InterfaceAlias -notmatch "Loopback|Virtual|Bluetooth" -and $_.PrefixOrigin -eq "Dhcp"
} | Select-Object -First 1).IPAddress
if ([string]::IsNullOrWhiteSpace($ip)) { $ip = "localhost" }

$title = @"

╔══════════════════════════════════════════════════╗
║         Remote OBS Relay — RUNNING               ║
╠══════════════════════════════════════════════════╣
║                                                  ║
║  Open Remote OBS app → Menu →                   ║
║  Enter in "Multiview WHEP URL":                  ║
║                                                  ║
║  http://$ip:8889/whep/multiview                  ║
║                                                  ║
║  The app should auto-discover via mDNS.          ║
║  Zoom keeps using OBS Virtual Camera.             ║
║                                                  ║
║  Close this window to stop the relay.            ║
║                                                  ║
╚══════════════════════════════════════════════════╝

"@
Write-Host $title -ForegroundColor Cyan

# ─── 8. cleanup on exit ────────────────────────────────────────
try {
    Read-Host "Press Enter to stop relay"
} finally {
    $_ = $ff, $mtx, $mdns | ForEach-Object { if ($_ -and !$_.HasExited) { $_.Kill() } }
    Write-Host "Relay stopped." -ForegroundColor Yellow
}
