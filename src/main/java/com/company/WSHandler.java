package com.company;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@WebSocket
public class WSHandler {

    private static final Random random = new Random();
    private static final Map<Session,String> users = new HashMap<>();

    private static String getUsername() {
        JSONParser parser = new JSONParser();
        JSONArray nouns = null;
        JSONArray adjectives = null;
        try {
            nouns = (JSONArray) parser.parse(new FileReader("/home/marios/netmusicplay/nouns-list.json"));
            adjectives = (JSONArray) parser.parse(new FileReader("/home/marios/netmusicplay/adjectives-list.json"));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        String adjective = (String) adjectives.get(random.nextInt(adjectives.size()-1));
        String noun = (String) nouns.get(random.nextInt(nouns.size()-1));
        adjective = adjective.substring(0, 1).toUpperCase() + adjective.substring(1);
        noun = noun.substring(0, 1).toUpperCase() + noun.substring(1);

        return String.format("%s%s",adjective,noun);
    }

    public static void broadcast(String author, String content) {
        JSONObject object = new JSONObject();
        object.put("author",author);
        object.put("content",content);
        object.put("nowListening",users.size());

        users.keySet().forEach((session -> {
            try {
                session.getRemote().sendString(object.toJSONString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    public void requested(String ip ,Song song) {
        Optional<Session> session = users.keySet().stream().filter(session1 -> session1.getRemoteAddress().getHostString().equals(ip)).findFirst();
        System.out.println(ip);
        System.out.println("ws request function called");
        session.ifPresent(session1 -> {
            System.out.println("Processing request ws");
            String username = users.get(session1);
            broadcast(username,String.format("Requested %s by %s",song.title,song.artist));
        });
    }

    public int getListening() {
        return users.size();
    }

    @OnWebSocketConnect
    public void connected(Session session) {
        String username = getUsername();
        users.put(session,username);
        broadcast("",username+" has joined the chat!");
    }

    @OnWebSocketClose
    public void closed(Session session, int status, String reason) {
        String username = users.get(session);
        users.remove(session);
        broadcast("",username+" has left the chat!");
    }

    @OnWebSocketMessage
    public void message(Session session, String message) {
        broadcast(users.get(session),message);
    }
}
