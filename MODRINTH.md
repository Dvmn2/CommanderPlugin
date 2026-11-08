# CommanderPlugin

Paper plugin that runs console commands when things happen on your server: a player joins, quits, dies, changes
world and more. Rules are managed in game with `/commander`.

## Features
- Run any console command on `join`, `first-join`, `quit`, `kick`, `death`, `respawn`, `world-change`,
  `advancement` and `server-start`.
- Placeholders such as `{player}`, `{uuid}`, `{world}` and `{x}` `{y}` `{z}` in commands.
- Optional delay, required permission and world filter per rule.
- Create, change, enable and delete rules in game; rules are saved automatically.
- Messages in Russian and English.

## Requirements
- Paper 1.21.11, Java 21
- No other plugins are required

## Installation
1. Put `CommanderPlugin-<version>.jar` into the `plugins` folder.
2. Start the server; `plugins/CommanderPlugin/config.yml` and `plugins/CommanderPlugin/rules.json` are created.
3. Create your first rule, for example:

```text
/commander create session-count
/commander event add session-count join
/commander event add session-count quit
/commander command add session-count scoreboard players add {player} SessionCount 1
```

Create the scoreboard objective yourself first: `/scoreboard objectives add SessionCount dummy`.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/commander list` | `commander.use` | List all rules |
| `/commander info <rule>` | `commander.use` | Show events, delay, conditions and commands of a rule |
| `/commander create <rule>` | `commander.edit` | Create an empty rule |
| `/commander delete <rule>` | `commander.edit` | Delete a rule |
| `/commander enable <rule>` | `commander.edit` | Enable a rule |
| `/commander disable <rule>` | `commander.edit` | Disable a rule |
| `/commander delay <rule> <seconds>` | `commander.edit` | Wait this many seconds before running the commands (0 to 86400, fractions allowed) |
| `/commander permission <rule> <permission>` | `commander.edit` | Run only for players that have the permission |
| `/commander permission <rule> clear` | `commander.edit` | Remove the permission requirement |
| `/commander event add <rule> <event>` | `commander.edit` | Run the rule on this event |
| `/commander event remove <rule> <event>` | `commander.edit` | Stop running the rule on this event |
| `/commander world add <rule> <world>` | `commander.edit` | Run only for players in this world (any world if none is set) |
| `/commander world remove <rule> <world>` | `commander.edit` | Remove a world from the filter |
| `/commander command add <rule> <command>` | `commander.edit` | Add a command; a leading `/` is optional |
| `/commander command remove <rule> <number>` | `commander.edit` | Remove a command by its number from `info` |
| `/commander reload` | `commander.reload` | Reload `config.yml` and `rules.json` |

## Permissions
| Permission | Default | Description |
|---|---|---|
| `commander.use` | op | Use `/commander`, list and view rules |
| `commander.edit` | op | Create and change rules |
| `commander.reload` | op | Use `/commander reload` |
| `commander.admin` | op | All of the above |

Rules run commands as the console, so give `commander.edit` only to people you trust with console access.

## Configuration
File: `plugins/CommanderPlugin/config.yml`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Message language; `auto` follows the player's client |
| `settings.max-rules` | int | `100` | `1..1000` | Maximum number of rules that can be created by command |
| `settings.max-commands-per-rule` | int | `20` | `1..100` | Maximum number of commands in one rule when adding by command |

```yaml
settings:
  language: auto
  max-rules: 100
  max-commands-per-rule: 20
```

### Events
| Event | Fires when | Extra placeholders |
|---|---|---|
| `join` | a player joins | none |
| `first-join` | a player joins for the first time (fires together with `join`) | none |
| `quit` | a player leaves for any reason | none |
| `kick` | a player leaves because they were kicked (fires together with `quit`) | none |
| `death` | a player dies | `{killer}` (only when another player killed them) |
| `respawn` | a player respawns; `{x}` `{y}` `{z}` `{world}` are the respawn point | none |
| `world-change` | a player changes world | `{from-world}`, `{to-world}` |
| `advancement` | a player completes an advancement (recipe unlocks are ignored) | `{advancement}` |
| `server-start` | the server finishes starting; no player, so permission and world filters are ignored | none |

### Placeholders
| Placeholder | Value |
|---|---|
| `{player}` | Player name |
| `{uuid}` | Player UUID |
| `{world}` | Player's world |
| `{x}` `{y}` `{z}` | Player's block coordinates |
| `{killer}` | Name of the killer (`death` only) |
| `{from-world}` `{to-world}` | Previous and new world (`world-change` only) |
| `{advancement}` | Advancement id, for example `minecraft:story/mine_stone` (`advancement` only) |

A command can only use placeholders that all events of its rule provide. If a value has no data at that moment
(for example `{killer}` after a fall), that command is skipped. Chat messages are never available as a
placeholder.

## Data
- `plugins/CommanderPlugin/rules.json` holds all rules. Back it up by copying the file.
- You can edit it by hand and run `/commander reload`. Invalid entries are skipped with a warning in the console.
- If the file is broken it is renamed to `rules.json.broken` and the plugin starts with no rules.
- To reset, stop the server (or run `/commander reload` after) and delete `rules.json`.

## Companion mod
None.

## Troubleshooting
- **The command does nothing.** Check `/commander info <rule>`: the rule must be `[on]`, have an event and a command.
  The console shows a warning once if a command is unknown or fails.
- **`{killer}` commands do not run.** Only deaths caused by another player have a killer.
- **A placeholder is rejected.** The rule's events do not provide it. See the Events table.
- **Console is noisy after each command.** Vanilla commands such as `scoreboard` print their result to the
  console.
- **`quit` rules do not run while the server stops.** Shutdown handling is not guaranteed.

## License
All rights reserved.
