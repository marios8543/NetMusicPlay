package com.company;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import spark.Service;

import java.io.*;
import java.util.*;
import static spark.Service.ignite;
import static spark.Spark.exception;
import static spark.Spark.internalServerError;


/** @noinspection unchecked*/
class Main {
    private static final Service server = ignite().port(4567).threadPool(200);
    public static final List<String> exts = Arrays.asList("mp3","flac");
    public static List<Song> songs = new ArrayList<>();
    private static final JSONParser parser = new JSONParser();
    static public String musicPath;

    public static void rescanLibrary(String jsonpath) {
        Set<File> fileTree = new HashSet<>();
        for (File entry : Objects.requireNonNull(new File(musicPath).listFiles())) {
            if(entry.isFile()) fileTree.add(entry);
            else fileTree.addAll(Arrays.asList(Objects.requireNonNull(entry.listFiles())));
        }
        songs = new ArrayList<>();
        JSONArray tobesaved = new JSONArray();
        fileTree.forEach((f)->{
            String[] tmp = f.getName().split("\\.");
            String ext = "";
            if(tmp.length>0){
                ext = tmp[tmp.length-1];
            }
            try {
                if(exts.contains(ext)){
                    Song song = new Song(f.getAbsoluteFile());
                    songs.add(song);
                    tobesaved.add(song.toJSON());
                }
            } catch (Exception ignored){ }
        });
        try{
            PrintWriter file = new PrintWriter(jsonpath);
            file.write(tobesaved.toJSONString());
            file.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void recoverLibrary(String jsonpath) {
        JSONArray libarray;
        try{
            libarray = (JSONArray)parser.parse(new FileReader(jsonpath));
        }
        catch (FileNotFoundException e){
            rescanLibrary(jsonpath);
            return;
        }
        catch (Exception e){
            e.printStackTrace();
            rescanLibrary(jsonpath);
            return;
        }
        if(libarray.size()>0){
            for (Object aLibarray : libarray) {
                Song song = new Song((JSONObject) aLibarray);
                songs.add(song);
            }
        }
        else {
            rescanLibrary(jsonpath);
        }
    }

    public static Song getSongById(String id){
        for(Song song : songs){
            if(song.id.equals(id)) return song;
        }
        return null;
    }

    public static void main(String[] args) {
        if(args.length<=0){
            System.out.println("No music path specified. Server will now exit");
            System.exit(2);
        }
        musicPath = args[0];
        recoverLibrary(musicPath+"webplayer_library_cache.json");
        songs.sort(Comparator.comparing(o -> o.title));
        server.staticFiles.location("/public");

        internalServerError((req, res) -> {
            res.type("application/json");
            return "{\"message\":\"Internal server error\"}";
        });

        exception(Exception.class,(exception,request,response)->{
            response.type("application/json");
            response.status(500);
            response.body(String.format("{\"message\":\"%s\"}",exception.getMessage()));
        });

        new RestApi(server);
        new RenderedPaths(server);
    }
}
