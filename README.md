# CommanderPlugin

Paper plugin that runs console commands in response to server events. Rules are created and edited in game
with `/commander` and stored in `rules.json`.

## Requirements
- Minecraft 1.21.11, Paper, Java 21
- No other plugins required; no companion mod

## Build and run
```bash
./gradlew build      # build/libs/CommanderPlugin-<version>.jar
./gradlew runServer  # Paper 1.21.11 test server
```

## Architecture
| Class | Responsibility |
|---|---|
| `CommanderPlugin` | composition root: wiring, enable/disable order, `reloadAll()` |
| `CommanderSettings` | immutable snapshot of `config.yml` |
| `Lang` | RU/EN MiniMessage texts; the only static mutable state (current language) |
| `Permissions` | permission constants |
| `RuleManager` | owns the current `RuleRegistry`, applies edits, queues saves |
| `ExecutionManager` | matches rules to an event, resolves placeholders, schedules and dispatches commands |
| `CommanderListener` | Bukkit events to `TriggerContext`, nothing else |
| `command.CommanderCommand` | Brigadier tree of `/commander` |
| `model.Rule` | immutable rule record with validation and `withX` copies |
| `model.RuleRegistry` | immutable index of rules by name and by event |
| `model.TriggerEvent` / `model.Placeholder` | event and placeholder vocabulary; which event provides which placeholder |
| `model.EditResult` | outcome enum of every edit; the command maps it to a `Lang` message |
| `storage.RuleStore` | `rules.json` reading and atomic writing on a dedicated thread |
| `storage.dto.*` | on-disk shape of `rules.json` |
| `util.Placeholders` | finds and substitutes `{token}` in command templates |

Threading:
- All Bukkit API calls, edits and command execution run on the main thread.
- `RuleRegistry` is immutable and published through a `volatile` field in `RuleManager`.
- `RuleStore` runs all disk access on one daemon thread, so reads never overtake queued writes.

Lifecycle:
- Enable: `saveDefaultConfig`, settings, language, `RuleManager.load`, `ExecutionManager`, listener, command, log line.
- Disable: cancel delayed commands, wait for pending writes, final save only if something was edited.
- Reload: `reloadAll()` re-reads `config.yml` and `rules.json` without restarting. Every edit is queued for saving
  immediately and the read waits behind queued writes, so a reload never loses an edit.

## Public API
None.

## Protocol
None.

## Data and files
`plugins/CommanderPlugin/rules.json`, format version `1`:

```json
{
  "version": 1,
  "rules": [
    {
      "name": "session-count",
      "enabled": true,
      "events": ["join", "quit"],
      "delay-seconds": 0.0,
      "permission": "",
      "worlds": [],
      "commands": ["scoreboard players add {player} SessionCount 1"]
    }
  ]
}
```

- Written atomically (temporary file, then move). Unknown fields are ignored, missing fields get defaults.
- Invalid entries (bad name, unknown event, invalid command, unsupported placeholder) are skipped with a warning.
- A file that is not valid JSON or is larger than 4 MiB is moved to `rules.json.broken` and the plugin starts
  with no rules.
- The file is created empty on the first start.

## Conventions and constraints
- Commands always run as the console, on the next tick at the earliest, never inside the event handler.
  Placeholder values are captured when the event fires, so a delay does not change them.
- Every placeholder value is checked against a strict pattern (`model.Patterns`) before substitution. A value
  that fails the check, or a missing value (for example `{killer}` without a player killer), skips that command.
- Only `{player}` style tokens are supported. No placeholder carries free text from a player (no chat message).
- `Rule`, `TriggerEvent` and `Placeholders` validate commands both at edit time and at load time, so a rule can
  never hold a command whose placeholders its events cannot provide.
- `kick` is derived from `PlayerQuitEvent` with reason `KICKED`, so it reports the final outcome. `quit` fires
  for a kick as well.
- Recipe unlocks (`recipes/...`) do not fire `advancement`.
- Permission and world conditions apply only to events with a player and are ignored for `server-start`.
- `settings.max-rules` and `settings.max-commands-per-rule` limit edits made by command, not what is read from
  `rules.json`.
- Feedback of vanilla commands (for example from `scoreboard`) is printed to the server console.
- Failed commands are logged once per rule and command until the next reload.

## License
All rights reserved.
