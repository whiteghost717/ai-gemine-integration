# Dokaibe Discord Companion

Requires Python 3.10+.

```bash
python -m venv .venv
# Linux/macOS: source .venv/bin/activate
# Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
cp .env.example .env
# Fill the environment variables, then:
python bot.py
```

Create a Discord application/bot in the Discord Developer Portal, invite it with `applications.commands` and `bot` scopes, and keep its token private. Configure the same bridge token and numeric Discord founder ID in the Minecraft plugin. The bot does not trust Discord display names. The plugin checks Discord IDs and the shared token again before accepting activation/deactivation.

The plugin bridge binds to localhost by default. If the bot runs on a separate VPS, expose it only through a TLS reverse proxy and firewall allowlist. Never forward the raw bridge port to the public internet.
