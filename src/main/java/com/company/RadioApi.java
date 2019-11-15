package com.company;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import spark.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.company.Main.getSongById;
import static com.company.Main.songs;
import static com.company.Main.wsHandler;
import static spark.Spark.halt;

class RadioApi {
    private static final List<Song> requests = new ArrayList<>();
    private static Song nowPlaying = null;
    private static int currentTime = 0;
    private static int lastIndex = -1;
    private static final Map<String,Long> requestLimits = new HashMap<>();
    private static List<String> skipVotes = new ArrayList<>();
    private static final int requestLimit = Integer.parseInt(System.getenv("request_limit"));
    private static boolean skip = false;

    RadioApi(Service server) {

        server.get("/api/radio",(req,res)->{
            res.type("application/json; charset=utf-8");
            JSONObject ret = new JSONObject();
            ret.put("song",nowPlaying.toJSON());
            ret.put("time",currentTime);

            Song[] upcoming = new Song[5];
            if (requests.size()>=5) for(int i=0;i<5;i++) upcoming[i] = requests.get(i);
            else {
                for(int i=0;i<requests.size();i++) upcoming[i] = requests.get(i);
                for(int i=requests.size();i<5;i++) {
                    Song s = songs.get(lastIndex+1+i-requests.size());
                    upcoming[i] = s;
                }
            }
            JSONArray arr = new JSONArray();
            for (Song anUpcoming : upcoming) arr.add(anUpcoming.toJSON());
            ret.put("upcoming",arr);
            return ret.toJSONString();
        });

        server.before("/api/radio/request",(request, response) -> {
            if (requestLimits.containsKey(request.ip())) {
                long currentTime = Instant.now().getEpochSecond();
                if ((currentTime-requestLimits.get(request.ip())) < requestLimit) {
                    halt(403,"You can only make a song request every "+requestLimit+" seconds");
                }
            }
        });

        server.get("/api/radio/request",(req,res) -> {
            Song song = getSongById(req.queryParams("id")!=null ? req.queryParams("id") : "");
            JSONObject object = new JSONObject();
            if (song!=null) {
                requests.add(song);
                res.type("application/json; charset=utf-8");
                res.status(200);

                object.put("queue_position",requests.size());
                requestLimits.put(req.ip(),Instant.now().getEpochSecond());

                wsHandler.requested(req.ip(),song);
            }
            else {
                res.status(404);
                object.put("message","Song does not exist");
            }
            return object.toJSONString();
        });
        
        server.before("/api/radio/voteSkip", (request, response) -> {
            if (skipVotes.contains(request.ip())) halt(403,"You've already voted to skip this song.");
        });
        
        server.get("/api/radio/voteSkip",(req,res) -> {
            skipVotes.add(req.ip());
            int votesToSkip;
            switch (wsHandler.getListening()) {
                case 1:
                    votesToSkip = 1;
                    break;
                case 2:
                    votesToSkip = 2;
                    break;
                default:
                    votesToSkip = (int)Math.ceil(wsHandler.getListening()/2);
                    break;
            }
            if (skipVotes.size()>=votesToSkip) {
                skip = true;
                wsHandler.broadcast("force_reload","");
                return "Skipping";
            }
            else {
                JSONObject object = new JSONObject();
                object.put("votes",skipVotes.size());
                object.put("votesToSkip",votesToSkip);
                res.type("application/json; charset=utf-8");
                wsHandler.broadcast("skip_votes",String.format("%s/%s",skipVotes.size(),votesToSkip));
                return object.toJSONString();
            }
        });

        new Thread(() -> {
            while (true) {
                currentTime = 0;
                skipVotes = new ArrayList<>();
                if (requests.size()>0) {
                    nowPlaying = requests.get(0);
                    requests.remove(0);
                }
                else {
                    lastIndex++;
                    nowPlaying = songs.get(lastIndex);
                }
                if (lastIndex==songs.size()-1) lastIndex = -1;
                for(int i=0;i<nowPlaying.length;i++) {
                    currentTime++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (skip) {
                        System.out.println("skipping");
                        skip = false;
                        break;
                    }
                }
            }
        }).start();
    }
}
