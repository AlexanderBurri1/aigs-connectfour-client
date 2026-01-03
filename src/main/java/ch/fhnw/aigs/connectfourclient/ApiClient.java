package ch.fhnw.aigs.connectfourclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiClient {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public User register(String userName, String password) throws Exception {
        String json = "{\"userName\":\"" + esc(userName) + "\",\"password\":\"" + esc(password) + "\"}";
        return post("/users/register", json, User.class);
    }

    public User login(String userName, String password) throws Exception {
        String json = "{\"userName\":\"" + esc(userName) + "\",\"password\":\"" + esc(password) + "\"}";
        return post("/users/login", json, User.class);
    }

    public Game newGame(String token, String gameType, int difficulty) throws Exception {
        String json = "{\"token\":\"" + esc(token) + "\",\"gameType\":\"" + esc(gameType) + "\",\"difficulty\":\"" + difficulty + "\"}";
        return post("/game/new", json, Game.class);
    }

    public Game move(String token, int col) throws Exception {
        String json = "{\"token\":\"" + esc(token) + "\",\"col\":\"" + col + "\"}";
        return post("/game/move", json, Game.class);
    }

    private <T> T post(String path, String json, Class<T> clazz) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + res.statusCode() + " - " + res.body());
        }
        return om.readValue(res.body(), clazz);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
