package com.company;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.datatype.Artwork;
import org.jaudiotagger.tag.reference.PictureTypes;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.sound.spi.FlacAudioFileReader;
import org.jflac.util.ByteData;
import org.jflac.util.WavWriter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import spark.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.company.Main.*;
import static java.util.Objects.requireNonNull;

class PlayerApi {
    private static int last_compare = 1;
    private static final Random rand = new Random();

    public PlayerApi(Service server){
        server.get("/api/rescanLibrary",(req,res)->{
            if(Main.scanInProgress){
                res.status(202);
                return null;
            }
            int lenold = songs==null ? 0 : songs.size();
            rescanLibrary(musicPath+"webplayer_library_cache.json");
            int lennew = songs.size();
            JSONObject ret = new JSONObject();
            ret.put("song_count",songs.size());
            ret.put("added",lennew-lenold);
            res.type("application/json; charset=utf-8");
            return ret.toJSONString();
        });

        server.get("/api/player",(req,res)->{
            int offset = Integer.parseInt(req.queryParams("s")!=null ? req.queryParams("s") : "0");
            int off_count = (songs.size()/30)*30<songs.size() ? (songs.size()/30)+1 : songs.size()/30;
            offset = offset*30>songs.size() ? 0 : offset;

            int sort = 1; //0:filename, 1:title, 2:artist, 3:album
            String sortBy = req.queryParams("sortBy");
            if(sortBy!=null){
                try{
                    int s = Integer.parseInt(sortBy);
                    if(s>=0 && s<=3){
                        sort = s;
                    }
                }
                catch (Exception ignored){}
            }
            if(sort!=last_compare){
                switch (sort){
                    case 0:
                        songs.sort(Comparator.comparing(o -> o.filename));
                        break;
                    case 1:
                        songs.sort(Comparator.comparing(o -> o.title));
                        break;
                    case 2:
                        songs.sort(Comparator.comparing(o -> o.artist));
                        break;
                    case 3:
                        songs.sort(Comparator.comparing(o -> o.album));
                        break;
                }
            }
            List<Song> sublist = songs.subList(offset*30,(offset*30)+30);
            JSONObject ret = new JSONObject();
            ret.put("offset_count",off_count);
            JSONArray retarr = new JSONArray();
            sublist.forEach((s)-> retarr.add(s.toJSON()));
            ret.put("songs",retarr);
            res.type("application/json; charset=utf-8");
            songs.sort(Comparator.comparing(o -> o.title));
            return ret.toJSONString();
        });

        server.get("/api/search",(req,res)->{
            JSONObject ret = new JSONObject();
            res.type("application/json; charset=utf-8");
            String query = req.queryParams("q");
            if(query==null || query.length()<3){
                res.status(400);
                ret.put("message","No search query or query shorter than 3 characters");
            }
            else {
                res.status(200);
                query = query.toLowerCase();
                JSONArray retarr = new JSONArray();
                for(Song s : songs){
                    if(s.matches(query)){
                        retarr.add(s.toJSON());
                    }
                }
                ret.put("songs",retarr);
            }
            return ret.toJSONString();
        });

        server.get("/api/fetchArt",(req,res)->{
            try {
                String path = req.queryParams("id")!=null ? req.queryParams("id") : "";
                path = musicPath+requireNonNull(getSongById(path)).path;
                File file = new File(path);
                if(!file.isFile()){
                    res.redirect("https://tzatzikiweeb.moe/static/img/defaultart.jpg");
                    return null;
                }
                Artwork artwork = AudioFileIO.read(file).getTag().getFirstArtwork();
                res.type(artwork.getMimeType());
                res.header("Content-Disposition","inline; filename=artwork."+PictureTypes.getInstanceOf().getValueForId(artwork.getPictureType()));
                HttpServletResponse raw = res.raw();
                raw.getOutputStream().write(artwork.getBinaryData());
                raw.getOutputStream().flush();
                raw.getOutputStream().close();
                return raw;
            }
            catch (Exception e){
                res.redirect("https://tzatzikiweeb.moe/static/img/defaultart.jpg");
                return null;
            }
        });

        server.get("/api/fetchSong",(req,res)->{
            String path = req.queryParams("id")!=null ? req.queryParams("id") : "";
            byte[] bytes;
            HttpServletResponse raw = res.raw();
            Song song = requireNonNull(getSongById(path));
            try{
                bytes = Files.readAllBytes(Paths.get(musicPath+song.path));
            }
            catch (NoSuchFileException e){
                res.status(404);
                return null;
            }
            String mimetype = "application/octet-stream";
            if(song.filename.endsWith("flac")) mimetype = "audio/flac";
            else if(song.filename.endsWith("mp3")) mimetype = "audio/mpeg";
            res.type(mimetype);
            if (req.headers().contains("range")) {
                String[] ranges = req.headers("range").split(",");
                res.status(206);
                for (String range : ranges) {
                    range.replace("bytes=","");
                    String[] rangeParts = range.split("-");
                    int startByte = Integer.parseInt(rangeParts[0]);
                    int endByte = Integer.parseInt(rangeParts[1]);
                    if(startByte<0 || endByte>bytes.length) server.halt(416,"Out of range");
                    for (int i=startByte;i<endByte;i++) raw.getOutputStream().write(bytes[i]);
                }
            }
            else {
                res.header("Content-Disposition","inline; filename="+song.filename);
                res.header("Content-Length",Integer.toString(bytes.length));
                raw.getOutputStream().write(bytes);
            }
            raw.getOutputStream().flush();
            raw.getOutputStream().close();
            return raw;
        });

        server.get("/api/transcodeFlac",(req,res)->{
            String path = req.queryParams("path")!=null ? req.queryParams("path") : "";
            HttpServletResponse raw = res.raw();
            WavWriter output = new WavWriter(raw.getOutputStream());
            File file = new File(musicPath+getSongById(path).path);
            if(file.isFile()){
                if(!file.getName().endsWith("flac")){
                    res.redirect("/api/fetchSong?path="+path);
                    return null;
                }
                FlacAudioFileReader reader = new FlacAudioFileReader();
                FLACDecoder decoder = new FLACDecoder(reader.getAudioInputStream(file));
                res.header("Content-Length",Long.toString(decoder.getTotalBytesRead()));
                decoder.addPCMProcessor(new PCMProcessor() {
                    @Override
                    public void processStreamInfo(StreamInfo streamInfo) {
                        try {
                            output.writeHeader(streamInfo);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void processPCM(ByteData byteData) {
                        try {
                            output.writePCM(byteData);
                        } catch (IOException ignored){}
                    }
                });
                decoder.decode();
                raw.getOutputStream().flush();
                raw.getOutputStream().close();
                return raw;
            }
            res.status(404);
            return null;

        });

        server.get("/api/kill",(req,res)->{
            if(req.ip().equals("0:0:0:0:0:0:0:1")){
                System.exit(0);
                return null;
            }
            else {
                res.type("application/json; charset=utf-8");
                res.status(403);
                return "{\"message\":\"Only localhost can do that\"}";
            }
        });

    }
}
