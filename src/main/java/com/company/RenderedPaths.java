package com.company;

import org.json.simple.JSONObject;
import spark.Service;

import javax.servlet.MultipartConfigElement;
import java.io.File;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;

import static com.company.Main.exts;
import static com.company.Main.musicPath;
import static com.company.Main.songs;

public class RenderedPaths {

    public RenderedPaths(Service server){
        server.post("/upload_song",(req,res)->{
            res.type("application/json");
            JSONObject ret = new JSONObject();
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
            try (InputStream is = req.raw().getPart("song").getInputStream()) {
                String filename = req.raw().getPart("song").getSubmittedFileName();
                String[] tmp = filename.split("\\.");
                String ext = "";
                if(tmp.length>0){
                    ext = tmp[tmp.length-1];
                }
                if(exts.contains(ext)){
                    File target = new File(musicPath+filename);
                    java.nio.file.Files.copy(is,target.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    songs.add(new Song("/srv/http/Music/"+filename));
                    ret.put("song_count",songs.size());
                    return ret.toJSONString();
                }
                else {
                    res.status(400);
                    ret.put("message","File is not a song (mp3,flac)");
                    return ret;
                }
            }
            catch (Exception e){
                res.status(500);
                res.type("application/json");
                ret.put("message",e.getMessage());
                return ret.toJSONString();

            }
        });

        server.get("/player",(req,res)->{
            res.redirect("index.html");
            return "";
        });

        server.get("/radio",(req,res)->{
            res.redirect("radio.html");
            return "";
        });
    }
}
