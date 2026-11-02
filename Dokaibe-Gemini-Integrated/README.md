# DOKAIBE AI — Paper 26.3

A founder-locked Minecraft server assistant with Adventure chat identity, local chat replies, SQLite activity logging, redstone activation input, Discord webhook reports, and an optional authenticated Discord companion bridge.

## Compatibility

- Paper 26.3 API, Java 25.
- Maven uses `io.papermc.paper:paper-api:26.3.build.230-stable` (`provided`) and shades Gson for JSON parsing.
- Build with JDK 25 and Maven 3.9+.

## Build

```bash
mvn clean package
```

Copy `target/dokaibe-ai-1.1.0.jar` into `plugins/`, start Paper once, then stop it and edit `plugins/DokaibeAI/config.yml`.

## Critical first-time security setup

1. Start server and run `/dokaibe whoami` as the intended founder.
2. Copy that UUID into `security.founder-uuid`.
3. Restart. Confirm `/dokaibe whoami` reports `Founder: true`.
4. Add admin UUIDs using `/dokaibe admin add <player>` while logged in as the configured founder.
5. Keep `security.founder-uuid` backed up. Only that UUID can use `/dokaibe reload` or change the admin allowlist.
6. Use online-mode authentication where possible. In offline/cracked mode, a username/UUID is not proof of identity; an attacker may be able to impersonate a username. Install and correctly configure a reputable authentication plugin, use a strong owner PIN/authentication layer, restrict panel access, and never expose the bridge publicly without TLS and firewall rules.

**Founder identity:** Zeroxxxy — Supreme Founder. The configured UUID, not the displayed name, determines founder authority. `[Hidden]` is not treated as a literal account or trusted identity; any additional owner must be explicitly configured by UUID and still cannot edit founder-locked controls.

## Chat identity

All Dokaibe output uses Paper's Adventure Components and MiniMessage formatting. Configure `identity.display-name`, `prefix`, `icon`, `primary-color`, `secondary-color`, and the role templates in `config.yml`. Player, admin, owner and system renderers are separate. No fake player account is created. The canonical founder recognition is always Zeroxxxy — Supreme Founder.

## Commands

- `/dokaibe help`
- `/dokaibe status`
- `/dokaibe on` and `/dokaibe off` (configured owner/founder UUID)
- `/dokaibe whoami`
- `/dokaibe players`
- `/dokaibe report` (owner)
- `/dokaibe reload` (founder only)
- `/dokaibe admin add <player>` and `/dokaibe admin remove <player>` (founder only)

## Redstone activation

The plugin stays installed while Dokaibe is inactive. Configure `activation.redstone.enabled: true`, then set `world`, `x`, `y`, and `z` to the coordinates of the **input block that receives the redstone signal**. Place a lever or wire so the selected block becomes powered when ON. A rising/falling power state changes Dokaibe state only when it differs from the current state; this is not a toggle-on-every-tick design.

Example: place a redstone lamp or other signal-receiving block at `world, 100, 64, 100`, wire a lever to it, then set those coordinates. In-game test: lever ON should broadcast `Dokaibe has awakened.`; lever OFF should broadcast `Dokaibe has gone offline.`. Use `powered-state: true` for power=ON means active; set false to invert. This implementation uses a configured redstone input block rather than command blocks executing unrestricted commands. Do not use repeating command blocks.

ASCII:

```text
[LEVER] ---- redstone dust ---- [INPUT BLOCK]
                                  x=100 y=64 z=100
                                      |
                         Paper BlockRedstoneEvent
                                      |
                              Dokaibe state ON/OFF
```

Emergency control: `/dokaibe on` or `/dokaibe off` as an authorized owner/founder.

## Gemini AI chat

The `@` trigger sends a message to Google's Gemini Developer API. The old `Dokaibe` trigger remains available for local shortcuts such as rules, help, online players, and founder identity.

1. Create an API key in Google AI Studio.
2. Keep the key private. On the server, put it in `plugins/DokaibeAI/config.yml` under `gemini.api-key` (or the compatibility key `ai.api-key`). The plugin also checks the `GEMINI_API_KEY` environment variable.
3. Set `ai.enabled: true`. The default model is `gemini-2.5-flash-lite`.
4. Activate Dokaibe with `/dokaibe on` as the configured founder/owner, or set `activation.start-enabled: true`.
5. In chat, use `@Hey Dokaibe, how are you?`.

AI calls use the plugin IO executor and do not block the Paper tick thread. A per-player cooldown and message length/output limits are configurable under `ai`. Conversation history is stored per player UUID in `plugins/DokaibeAI/memory/` and survives restarts. It retains the configured number of recent exchanges. Deleting a player's UUID JSON file clears that player's saved memory.

**Privacy:** Player messages typed after `@` are sent to Google for generation. Review Google's Gemini API free-tier data-use terms and tell players that their messages may be processed by Google. Do not use this feature for passwords, private data, or sensitive information. Never commit the live API key or server config to GitHub.

## Discord

### Webhook reports

Set `discord.webhook-url` to a private incoming webhook URL. Join/leave, death (if enabled), activation and shutdown notices are sent asynchronously. Treat the URL as a password.

### Optional companion bot bridge

The bridge is disabled by default. To enable:

1. Generate a random secret of at least 32 characters.
2. Set `discord.bridge-enabled: true`, `bridge-token`, and numeric `owner-user-ids` / `admin-user-ids` in config.
3. Keep `bridge-host: 127.0.0.1` if bot and Minecraft plugin share a machine. If they are on separate machines, put a TLS reverse proxy and firewall in front of the bridge; do not expose plain HTTP publicly.
4. Copy `discord-companion/.env.example` to `.env`, set `DISCORD_BOT_TOKEN`, `DOKAIBE_BRIDGE_URL`, and the same bridge token.
5. Install dependencies with `python -m pip install -r requirements.txt`, then run `python bot.py`.
6. Discord `/dokaibe_on` and `/dokaibe_off` are founder-Discord-ID-only. `/dokaibe_status` is readable by anyone who can invoke the bot command. Plugin-side allowlists are authoritative.

Never commit `.env`, bot tokens, webhook URLs, or bridge tokens.

## Database

SQLite file is created in the plugin data directory. Current build stores an append-only activity log for joins/leaves and admin list changes. It is safe to back up while the server is stopped.

## What is implemented vs. not yet implemented

Implemented in this deliverable: identity formatting, UUID founder/admin checks, activation state, redstone activation input, legacy local chat answers, Gemini-powered `@` chat, per-player UUID-based persistent conversation memory, cooldowns and message limits, SQLite activity logging, join/leave/death reporting, Discord webhook delivery, Discord status/on/off bridge, Maven build and GitHub Actions build.

Not implemented yet: PIN authentication, full structured AI action registry, confirmation queue, temporary OP, maintenance mode, full TPS/MSPT dashboard, failed-login telemetry, Discord admin action suite, and comprehensive test suite. `/dokaibe auth`, `confirm`, and `cancel` deliberately report unavailable rather than pretending they work. No arbitrary console-command execution is exposed. Do not treat this first build as the complete master-prompt feature set or deploy it as the only security boundary on an offline-mode server.

## Testing checklist

1. Build with JDK 25 and Maven.
2. Install JAR on a Paper 26.3 test server.
3. Verify startup and the offline-mode warning.
4. Set founder UUID and verify `whoami`.
5. Verify non-founder cannot reload or change admin list.
6. Verify founder can add/remove an admin UUID.
7. Test `/dokaibe on`, `/dokaibe off`, and status.
8. Test configured redstone input transitions ON and OFF.
9. Test player chat with `Dokaibe hello`, `Dokaibe rules`, `Dokaibe help`, and `Dokaibe who is online`.
10. Configure a test webhook and verify join/leave/death/activation reports.
11. Enable bridge with a test secret; verify status, then test on/off with an allowed Discord owner ID and a non-allowed ID.
12. Restart and verify configuration and SQLite log persist.

## License

Private project starter. Add your preferred license before public redistribution.

## Existing server upgrade note

`saveDefaultConfig()` does not overwrite an existing `plugins/DokaibeAI/config.yml`. After installing this update, manually merge the Gemini settings into that live file and restart the server. The live key can be stored as `gemini.api-key` or `ai.api-key`; the source config contains only blank placeholders.

For the AI listener to accept chat, Dokaibe must be active. Either set `activation.start-enabled: true` in the live config before restarting, or configure the founder UUID and use `/dokaibe on`.
