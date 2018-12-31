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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.company.Main.musicPath;
import static com.company.Main.rescanLibrary;
import static com.company.Main.songs;

class RestApi {
    private static int last_compare = 1;
    private static final Random rand = new Random();

    public RestApi(Service server){
        server.get("/api/rescanLibrary",(req,res)->{
            int lenold = songs==null ? 0 : songs.size();
            rescanLibrary(musicPath+"webplayer_library_cache.json");
            int lennew = songs.size();
            JSONObject ret = new JSONObject();
            ret.put("song_count",songs.size());
            ret.put("added",lennew-lenold);
            res.type("application/json");
            return ret.toJSONString();
        });

        server.get("/api/player",(req,res)->{
            int offset = Integer.parseInt(req.queryParamOrDefault("s","0"));
            int off_count = (songs.size()/30)*30<songs.size() ? (songs.size()/30)+1 : songs.size()/30;
            offset = offset*30>songs.size() ? 0 : offset;

            int sort = 1; //0:filename, 1:title, 2:artist, 3:album
            String sortBy = req.queryParamOrDefault("sortBy",null);
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
                        last_compare = 0;
                        break;
                    case 1:
                        songs.sort(Comparator.comparing(o -> o.title));
                        last_compare = 1;
                        break;
                    case 2:
                        songs.sort(Comparator.comparing(o -> o.artist));
                        last_compare = 2;
                        break;
                    case 3:
                        songs.sort(Comparator.comparing(o -> o.album));
                        last_compare = 3;
                        break;
                }
            }
            List<Song> sublist = songs.subList(offset*30,(offset*30)+30);
            JSONObject ret = new JSONObject();
            ret.put("offset_count",off_count);
            JSONArray retarr = new JSONArray();
            sublist.forEach((s)-> retarr.add(s.toJSON()));
            ret.put("songs",retarr);
            res.type("application/json");
            return ret.toJSONString();
        });

        server.get("/api/search",(req,res)->{
            JSONObject ret = new JSONObject();
            res.type("application/json");
            String query = req.queryParamOrDefault("q",null);
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

        server.get("/api/radio",(req,res)->{
            String i = req.queryParamOrDefault("idx",Integer.toString(rand.nextInt(songs.size())));
            int idx = Integer.parseInt(i);
            if(idx<0 || idx>=songs.size()){
                idx = rand.nextInt(songs.size());
            }
            Song song = songs.get(idx);
            res.type("application/json");
            JSONObject ret = song.toJSON();
            ret.put("index",idx);
            return ret.toJSONString();
        });

        server.get("/api/fetchArt",(req,res)->{
            try {
                String path = req.queryParamOrDefault("path","");
                path = musicPath+path;
                File file = new File(path);
                if(!file.isFile()){
                    res.redirect("/img/defaultart_"+rand.nextInt(4)+".jpg");
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
                res.redirect("/img/defaultart_"+rand.nextInt(4)+".jpg");
                return null;
            }
        });

        server.get("/api/fetchSong",(req,res)->{
            String path = req.queryParamOrDefault("path","");
            byte[] bytes;
            HttpServletResponse raw = res.raw();
            try{
                bytes = Files.readAllBytes(Paths.get(musicPath+path));
            }
            catch (NoSuchFileException e){
                throw new FileNotFoundException();
            }
            String ext = path.split("\\.")[path.split("\\.").length-1];
            String mimetype;
            if(ext.equals("mp3")){
                mimetype = "audio/mpeg";
            }
            else if(ext.equals("flac")){
                mimetype = "audio/x-flac";
            }
            else {
                mimetype = "application/octet-stream";
            }
            res.type(mimetype);
            res.header("Content-Disposition","inline; filename="+path.split("/")[path.split("/").length-1]);
            raw.getOutputStream().write(bytes);
            raw.getOutputStream().flush();
            raw.getOutputStream().close();
            return raw;
        });

        server.get("/api/transcodeFlac",(req,res)->{
            String path = req.queryParamOrDefault("path","");
            HttpServletResponse raw = res.raw();
            WavWriter output = new WavWriter(raw.getOutputStream());
            File file = new File(musicPath+path);
            if(file.isFile()){
                if(!file.getName().endsWith("flac")){
                    res.redirect("/api/fetchSong?path="+path);
                    return null;
                }
                FlacAudioFileReader reader = new FlacAudioFileReader();
                FLACDecoder decoder = new FLACDecoder(reader.getAudioInputStream(file));
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
            throw new FileNotFoundException();
        });

        server.get("/api/kill",(req,res)->{
            if(req.ip().equals("0:0:0:0:0:0:0:1")){
                System.exit(0);
                return null;
            }
            else {
                res.type("application/json");
                res.status(403);
                return "{\"message\":\"Only localhost can do that\"}";
            }
        });

    }
}
