-- Remote OBS Relay — add via OBS Tools → Scripts
-- Opens a separate Multiview projector and auto-launches the relay.
obs = obslua
local dir = script_path()
local bat = dir .. "Start Remote OBS Relay.bat"

function script_load(settings)
    obs.timer_add(open_multiview, 1000)
    os.execute('start "" "' .. bat .. '"')
end

function open_multiview()
    obs.obs_frontend_open_projector("Multiview", -1, "", "Remote OBS Multiview")
    obs.timer_remove(open_multiview)
end
