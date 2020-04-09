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
import java.net.HttpCookie;
import java.util.*;
import java.util.List;


@WebSocket
public class WSHandler {

    public static class Username {
        public String username;
        public static int adjectiveIdx;
        public static int nounIdx;

        public Username(Session session) {
            this();
            try {
                Optional<HttpCookie> cookieSearchResult = session.getUpgradeRequest().getCookies().stream().filter(httpCookie -> httpCookie.getName().equals("usernameId")).findFirst();
                System.out.println("Running session constructor");
                System.out.println(session.getUpgradeRequest().getCookies().size());
                if (cookieSearchResult.isPresent()) {
                    String usernameId = cookieSearchResult.get().getValue();
                    nounIdx = Integer.parseInt(usernameId.split("-")[0]);
                    adjectiveIdx = Integer.parseInt(usernameId.split("-")[1]);

                    username = formatUsername(adjectives.get(adjectiveIdx).toString(), nouns.get(nounIdx).toString());
                }
                else throw new Exception("Internal Flow Exception");
            }
            catch (Exception ignored) {
            }
        }

        public Username() {
            int[] res = getUsername();
            adjectiveIdx = res[0];
            nounIdx = res[1];
            username = formatUsername(adjectives.get(adjectiveIdx).toString(), nouns.get(nounIdx).toString());
        }

        public static String getCookieString() {
            return String.format("%d-%d", nounIdx, adjectiveIdx);
        }
    }

    private static final Random random = new Random();
    private static final Map<Session,String> users = new HashMap<>();
    private static final String list_location = System.getenv("list_location");
    private static JSONArray nouns;
    private static JSONArray adjectives;

    private static void  populateArrays() {
        if (nouns != null && adjectives != null) return;
        JSONParser parser = new JSONParser();
        try {
            nouns = (JSONArray) parser.parse(new FileReader(list_location+"/nouns-list.json"));
            adjectives = (JSONArray) parser.parse(new FileReader(list_location+"/adjectives-list.json"));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private static String formatUsername(String adjective, String noun) {
        adjective = adjective.substring(0, 1).toUpperCase() + adjective.substring(1);
        noun = noun.substring(0, 1).toUpperCase() + noun.substring(1);
        return String.format("%s%s",adjective,noun);
    }

    private static int[] getUsername() {
        populateArrays();
        int adjective = random.nextInt(adjectives.size()-1);
        int noun = random.nextInt(nouns.size()-1);
        return new int[]{adjective, noun};
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
        String username = (new Username(session)).username;
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
