barbatos — Phase 2: Cache, tmux & Debug Sessions
Goal
Add infrastructure for tmux-based debug sessions: cache directory management, tmux dependency validation, a debug command that creates tmux sessions, persists them in sessions.toml, attaches to tmux, and cleans up dead sessions on return.

Proposed Changes
Infrastructure Files
[NEW] 
CacheManager.kt
Manages ~/.cache/barbatos/ directory:

ensureCacheDir() — checks if dir exists, creates if missing, silently ignores errors (permission etc.)
cacheDir(): String — returns the resolved path
Called before every file read/write and on startup
[NEW] 
TmuxManager.kt
Wraps all tmux interactions:

checkTmux(): Boolean — runs which tmux + checks execution access, returns false if unavailable
createSession(name: String) — runs tmux new-session -d -s <name>
attachSession(name: String) — restores terminal, runs tmux attach-session -t <name> via system(), then re-enables raw mode when user returns
sessionExists(name: String): Boolean — runs tmux has-session -t <name>
Every public method calls checkTmux() first
IMPORTANT

tmux attach requires cooked mode: before calling tmux attach, we must restore the terminal to normal mode (disableRawMode), then re-enable raw mode when the user returns. Otherwise tmux will inherit the broken terminal state.

[NEW] 
SessionStore.kt
Reads/writes ~/.cache/barbatos/sessions.toml:

load(): SessionData — parses the TOML file (simple hand-rolled parser, no external lib)
save(data: SessionData) — writes the TOML file
addSession(name: String, createdAt: String) — loads, appends, saves
removeSession(name: String) — loads, removes from ids list + removes block, saves
Calls CacheManager.ensureCacheDir() before every read/write
TOML format:

toml
ids = [ "a1b2c", "x9y8z" ]
[a1b2c]
created_at = "29/03 19:34"
[x9y8z]
created_at = "29/03 20:01"
[NEW] 
CommandExecutor.kt
Dispatches submitted commands to their handlers:

execute(command: String, state: AppState): Boolean — returns true if handled
For now only debug is implemented
Future commands (clear, about, help, etc.) will be added here later
Modified Files
[MODIFY] 
CommandRegistry.kt
Replace attach → debug with description "start a new debug session"
[MODIFY] 
Main.kt
Startup: call CacheManager.ensureCacheDir() + TmuxManager.checkTmux() (exit with red error if tmux unavailable)
Command submission: route through CommandExecutor.execute() instead of just adding to history
After tmux return: check if session still exists, clean up sessions.toml if not
[MODIFY] 
Renderer.kt
Add RED ANSI color constant for tmux-unavailable error message
Debug Command Flow
SessionStore
TmuxManager
CommandExecutor
Main Loop
User
SessionStore
TmuxManager
CommandExecutor
Main Loop
User
disableRawMode → system("tmux attach") → enableRawMode
alt
[session dead]
types "debug" + Enter
execute("debug", state)
checkTmux()
true
generate 5-char alphanumeric id
createSession(id)
addSession(id, "29/03 19:34")
attachSession(id)
user returned
sessionExists(id)
removeSession(id)
done
re-render TUI
Key Behaviors
Cache Directory
Path: ~/.cache/barbatos/
Created silently on first access
Permission errors are ignored (we continue)
tmux Check
On startup: if tmux unreachable → print light red error message → exit
Before every tmux call: re-verify (in case $PATH changed etc.)
Session ID
5 alphanumeric chars (a-z0-9), randomly generated
Example: k3m9x
sessions.toml
Simple TOML format, hand-parsed (no external dependency)
ids list at root + one [section] per session
created_at formatted as dd/MM HH:mm
tmux attach/detach lifecycle
User types debug → Enter
New tmux session created in background (-d)
Session registered in sessions.toml
Terminal restored to cooked mode
tmux attach-session -t <id> runs (tmux takes over the screen)
User exits tmux (via exit or detach ctrl+b d)
Terminal re-enters raw mode
Check if session still alive → cleanup if dead
TUI re-renders
Open Questions
IMPORTANT

Home directory resolution: I'll use getenv("HOME") to resolve ~. This is standard on macOS. Is that acceptable?

Verification Plan
Automated Tests
Build succeeds
Run binary → verify tmux check passes (tmux is installed)
Run debug command → verify tmux session is created
Verify sessions.toml is created with correct format
Exit the tmux session → verify return to TUI
Verify dead session is cleaned from sessions.toml
Manual Verification
User runs the binary and tests the full debug flow