package com.dokaibe.ai;

import com.dokaibe.DokaibePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gemini chat integration. Conversation history is stored separately per player UUID.
 * Network and disk work is performed on the plugin IO executor, never on the server thread.
 */
public final class GeminiChatService {
    private static final String DEFAULT_MODEL = "gemini-2.5-flash-lite";
    private static final Type TURN_LIST_TYPE = new TypeToken<List<Turn>>() {}.getType();

    private final DokaibePlugin plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ConcurrentHashMap<UUID, List<Turn>> memories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastRequestAt = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public GeminiChatService(DokaibePlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(UUID playerId, String playerName, String prompt) {
        if (!plugin.getConfig().getBoolean("ai.enabled", true)) {
            send(playerId, "My AI chat is currently disabled.");
            return;
        }

        String key = apiKey();
        if (key.isBlank()) {
            plugin.getLogger().warning("Gemini is enabled but no API key is configured. Set ai.api-key or GEMINI_API_KEY.");
            send(playerId, "My Gemini connection is not configured yet. Please tell a server administrator.");
            return;
        }

        String message = prompt == null ? "" : prompt.trim();
        int maxChars = clamp(plugin.getConfig().getInt("ai.max-message-length", 500), 50, 2000);
        if (message.isBlank()) {
            send(playerId, "Write a message after @. Example: @Hey Dokaibe!");
            return;
        }
        if (message.length() > maxChars) {
            send(playerId, "That message is too long. Please keep it under " + maxChars + " characters.");
            return;
        }

        int cooldown = clamp(plugin.getConfig().getInt("ai.cooldown-seconds", 10), 1, 120);
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (inFlight.contains(playerId)) {
                send(playerId, "I'm still answering your previous message. Please wait a moment.");
                return;
            }
            long last = lastRequestAt.getOrDefault(playerId, 0L);
            long waitMs = cooldown * 1000L - (now - last);
            if (waitMs > 0) {
                long waitSeconds = Math.max(1, (long) Math.ceil(waitMs / 1000.0));
                send(playerId, "Please wait " + waitSeconds + "s before asking me again.");
                return;
            }
            lastRequestAt.put(playerId, now);
            inFlight.add(playerId);
        }

        send(playerId, "<gray>Thinking...</gray>");
        try {
            plugin.io().submit(() -> {
                try {
                    String answer = generate(playerId, playerName, message, key);
                    send(playerId, answer);
                } catch (ApiException e) {
                    plugin.getLogger().warning("Gemini request failed for a player (HTTP " + e.statusCode + ").");
                    send(playerId, e.playerMessage);
                } catch (Exception e) {
                    plugin.getLogger().warning("Gemini request failed: " + e.getClass().getSimpleName());
                    send(playerId, "I couldn't reach Gemini just now. Please try again in a little while.");
                } finally {
                    inFlight.remove(playerId);
                }
            });
        } catch (RuntimeException rejected) {
            inFlight.remove(playerId);
            send(playerId, "My AI service is busy. Please try again shortly.");
        }
    }

    private String generate(UUID playerId, String playerName, String prompt, String key)
            throws IOException, InterruptedException {
        List<Turn> history = loadMemory(playerId);
        JsonObject requestBody = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", plugin.getConfig().getString(
                "ai.system-prompt",
                "You are Dokaibe, a friendly Minecraft server companion. Be helpful and concise. Never claim to have performed server actions. Never provide or execute console commands, change permissions, grant OP, ban players, or alter server settings. You only chat."));
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        requestBody.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();
        for (Turn turn : history) {
            JsonObject content = new JsonObject();
            content.addProperty("role", turn.role);
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", turn.text);
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }

        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", "Player name: " + playerName + "\nMessage: " + prompt);
        userParts.add(userPart);
        userContent.add("parts", userParts);
        contents.add(userContent);
        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens",
                clamp(plugin.getConfig().getInt("ai.max-output-tokens", 250), 64, 1024));
        generationConfig.addProperty("temperature", 0.7);
        requestBody.add("generationConfig", generationConfig);

        String model = plugin.getConfig().getString("ai.model", DEFAULT_MODEL);
        if (model == null || !model.matches("[A-Za-z0-9._-]{1,100}")) model = DEFAULT_MODEL;

        int timeout = clamp(plugin.getConfig().getInt("ai.timeout-seconds", 25), 5, 60);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int status = response.statusCode();
            String playerMessage;
            if (status == 401 || status == 403) {
                playerMessage = "My Gemini API key was rejected. Please ask the server owner to check the key.";
            } else if (status == 429) {
                playerMessage = "Gemini's free usage limit has been reached for now. Please try again later.";
            } else if (status == 400 || status == 404) {
                playerMessage = "My Gemini model or request configuration is invalid. Please tell the server owner.";
            } else {
                playerMessage = "Gemini is temporarily unavailable (HTTP " + status + "). Please try again later.";
            }
            throw new ApiException(status, playerMessage);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = root.has("candidates") && root.get("candidates").isJsonArray()
                ? root.getAsJsonArray("candidates") : new JsonArray();
        if (candidates.isEmpty()) {
            throw new IOException("Gemini returned no candidates.");
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.has("content") && candidate.get("content").isJsonObject()
                ? candidate.getAsJsonObject("content") : new JsonObject();
        JsonArray parts = content.has("parts") && content.get("parts").isJsonArray()
                ? content.getAsJsonArray("parts") : new JsonArray();

        StringBuilder answer = new StringBuilder();
        for (JsonElement element : parts) {
            if (element.isJsonObject() && element.getAsJsonObject().has("text")) {
                if (!answer.isEmpty()) answer.append('\n');
                answer.append(element.getAsJsonObject().get("text").getAsString());
            }
        }
        String cleanAnswer = answer.toString().trim();
        if (cleanAnswer.isBlank()) {
            throw new IOException("Gemini returned an empty text response.");
        }

        history.add(new Turn("user", "Player name: " + playerName + "\nMessage: " + prompt));
        history.add(new Turn("model", cleanAnswer));
        int maxHistoryMessages = clamp(plugin.getConfig().getInt("ai.max-history-messages", 10), 1, 30);
        int maxStoredTurns = maxHistoryMessages * 2;
        while (history.size() > maxStoredTurns) history.remove(0);
        memories.put(playerId, history);
        saveMemory(playerId, history);

        return cleanAnswer;
    }

    private String apiKey() {
        String key = plugin.getConfig().getString("gemini.api-key", "");
        if (key == null || key.isBlank()) key = plugin.getConfig().getString("ai.api-key", "");
        if (key == null || key.isBlank()) key = System.getenv("GEMINI_API_KEY");
        return key == null ? "" : key.trim();
    }

    private List<Turn> loadMemory(UUID playerId) {
        return memories.computeIfAbsent(playerId, id -> {
            Path file = memoryFile(id);
            if (!Files.isRegularFile(file)) return new ArrayList<>();
            try {
                List<Turn> loaded = gson.fromJson(Files.readString(file), TURN_LIST_TYPE);
                return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load saved AI memory for UUID " + id + ".");
                return new ArrayList<>();
            }
        });
    }

    private void saveMemory(UUID playerId, List<Turn> history) {
        try {
            Path file = memoryFile(playerId);
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(history), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save AI memory for UUID " + playerId + ".");
        }
    }

    private Path memoryFile(UUID playerId) {
        return plugin.getDataFolder().toPath().resolve("memory").resolve(playerId + ".json");
    }

    private void send(UUID playerId, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !plugin.isActive()) return;
            plugin.sendAiMessage(player, message);
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Turn {
        final String role;
        final String text;

        Turn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private static final class ApiException extends RuntimeException {
        final int statusCode;
        final String playerMessage;

        ApiException(int statusCode, String playerMessage) {
            super("Gemini HTTP " + statusCode);
            this.statusCode = statusCode;
            this.playerMessage = playerMessage;
        }
    }
}
