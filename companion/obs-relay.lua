-- Remote OBS Relay — add via OBS Tools → Scripts
-- Auto-launches the relay when OBS starts.
obs = obslua
local dir = script_path()
local bat = dir .. "Start Remote OBS Relay.bat"

function script_load(settings)
    os.execute('start "" "' .. bat .. '"')
end
