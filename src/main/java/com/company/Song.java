package com.company;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;

class Song {
    final String path;
    final String filename;
    String title;
    String artist;
    String album;
    String ext;
    long length;

    public Song(File filearg) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException {
        path = filearg.getAbsolutePath().replace(Main.musicPath,"");
        filename = filearg.getName();
        String[] tmp = filearg.getName().split("\\.");
        ext = "";
        if(tmp.length>0){
            ext = tmp[tmp.length-1];
        }
        AudioFile file = AudioFileIO.read(filearg);
        Tag tag = file.getTag();
        title = tag.getFirst(FieldKey.TITLE)!=null && !tag.getFirst(FieldKey.TITLE).equals("") ? tag.getFirst(FieldKey.TITLE) : filename;
        artist = tag.getFirst(FieldKey.ARTIST)!=null && !tag.getFirst(FieldKey.ARTIST).equals("") ? tag.getFirst(FieldKey.ARTIST) : "Unknown artist";
        album = tag.getFirst(FieldKey.ALBUM)!=null && !tag.getFirst(FieldKey.ALBUM).equals("") ? tag.getFirst(FieldKey.ALBUM) : "Unknown album";
        length = file.getAudioHeader().getTrackLength();
    }

    public Song(String patharg) throws IOException, TagException, InvalidAudioFrameException, CannotReadException, ReadOnlyFileException {
        path = patharg.replace(Main.musicPath,"");
        filename = new File(patharg).getName();
        String[] tmp = patharg.split("\\.");
        ext = "";
        if(tmp.length>0){
            ext = tmp[tmp.length-1];
        }
        AudioFile file = AudioFileIO.read(new File(patharg));
        Tag tag = file.getTag();
        title = tag.getFirst(FieldKey.TITLE)!=null && !tag.getFirst(FieldKey.TITLE).equals("") ? tag.getFirst(FieldKey.TITLE) : filename;
        artist = tag.getFirst(FieldKey.ARTIST)!=null && !tag.getFirst(FieldKey.ARTIST).equals("") ? tag.getFirst(FieldKey.ARTIST) : "Unknown artist";
        album = tag.getFirst(FieldKey.ALBUM)!=null && !tag.getFirst(FieldKey.ALBUM).equals("") ? tag.getFirst(FieldKey.ALBUM) : "Unknown album";
        length = file.getAudioHeader().getTrackLength();
    }

    public Song(JSONObject obj){
        path = (String)obj.get("path");
        filename = (String)obj.get("filename");
        title = (String)obj.get("title");
        artist = (String)obj.get("artist");
        album = (String)obj.get("album");
        ext = (String)obj.get("ext");
        length = (long)obj.get("length");
    }

    /** @noinspection unchecked, unchecked */
    public JSONObject toJSON(){
        JSONObject ret = new JSONObject();
        ret.put("path",path);
        ret.put("filename",filename);
        ret.put("title",title);
        ret.put("artist",artist);
        ret.put("album",album);
        ret.put("length",length);
        return ret;
    }
    
    public boolean matches(String query){
        String fl = filename.toLowerCase();
        String ar = artist.toLowerCase();
        String tl = title.toLowerCase();
        String al = album.toLowerCase();
        boolean ret = false;
        try {
            ret = (fl.contains(query) || ar.contains(query) || tl.contains(query) || al.contains(query));
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return ret;
    }

    public String toString(){
        return String.format("%s -%s (%s)",artist,title,album);
    }
}
