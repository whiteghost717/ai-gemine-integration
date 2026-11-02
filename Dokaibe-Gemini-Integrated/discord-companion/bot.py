import os
import aiohttp
import discord
from discord import app_commands
from discord.ext import commands

BOT_TOKEN = os.getenv("DISCORD_BOT_TOKEN", "")
BRIDGE_URL = os.getenv("DOKAIBE_BRIDGE_URL", "http://127.0.0.1:8765").rstrip("/")
BRIDGE_TOKEN = os.getenv("DOKAIBE_BRIDGE_TOKEN", "")

intents = discord.Intents.none()
bot = commands.Bot(command_prefix="!", intents=intents)

async def bridge(path: str, user_id: int):
    if not BRIDGE_TOKEN:
        raise RuntimeError("DOKAIBE_BRIDGE_TOKEN is not configured")
    timeout = aiohttp.ClientTimeout(total=8)
    headers = {"X-Dokaibe-Token": BRIDGE_TOKEN, "X-Discord-User": str(user_id)}
    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.post(BRIDGE_URL + path, headers=headers) as response:
            body = await response.text()
            if response.status >= 400:
                raise RuntimeError(f"Bridge rejected request ({response.status}): {body}")
            return body

@bot.event
async def on_ready():
    await bot.tree.sync()
    print(f"Dokaibe companion logged in as {bot.user}")

@bot.tree.command(name="dokaibe_status", description="Show Dokaibe server status")
async def status(interaction: discord.Interaction):
    try:
        result = await bridge("/status", interaction.user.id)
        await interaction.response.send_message(f"**[DOKAIBE AI]** `{result}`", ephemeral=True)
    except Exception as exc:
        await interaction.response.send_message(f"Dokaibe bridge error: {exc}", ephemeral=True)

@bot.tree.command(name="dokaibe_on", description="Founder-only: activate Dokaibe")
async def activate(interaction: discord.Interaction):
    try:
        result = await bridge("/on", interaction.user.id)
        await interaction.response.send_message(f"Activation request: `{result}`", ephemeral=True)
    except Exception as exc:
        await interaction.response.send_message(f"Request rejected: {exc}", ephemeral=True)

@bot.tree.command(name="dokaibe_off", description="Founder-only: deactivate Dokaibe")
async def deactivate(interaction: discord.Interaction):
    try:
        result = await bridge("/off", interaction.user.id)
        await interaction.response.send_message(f"Shutdown request: `{result}`", ephemeral=True)
    except Exception as exc:
        await interaction.response.send_message(f"Request rejected: {exc}", ephemeral=True)

if __name__ == "__main__":
    if not BOT_TOKEN:
        raise SystemExit("Set DISCORD_BOT_TOKEN in your environment")
    bot.run(BOT_TOKEN)
