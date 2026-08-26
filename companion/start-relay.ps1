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
$cam = "OBS Virtual Camera"
Write-Host "Capturing '$cam' → RTMP :1935..." -ForegroundColor Green
$ffmpegArgs = @(
    "-f", "dshow", "-framerate", "$Fps", "-i", "video=$cam"
    "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency"
    "-f", "flv", "rtmp://localhost:1935/live/program"
)
$ff = Start-Process -FilePath "ffmpeg" -ArgumentList $ffmpegArgs -WindowStyle Hidden -PassThru
Start-Sleep 1
if ($ff.HasExited) { Die "ffmpeg failed. Is OBS running with Virtual Camera enabled?" }

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
║  Enter in "Program WHEP URL":                    ║
║                                                  ║
║  http://$ip:8889/whep/program                    ║
║                                                  ║
║  The app should auto-discover via mDNS.          ║
║  Preview in app uses screenshots (no WHEP).      ║
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
