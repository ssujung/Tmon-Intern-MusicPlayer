package com.tmon.sujung26.musicplayer.database;

import android.database.Cursor;
import android.provider.MediaStore;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class MusicItemDTO extends RealmObject {
    @PrimaryKey
    private long musicId;
    @Required
    private String musicTitle;
    private String artistName;
    private long albumId;
    private long duration;
    private int index;

    public MusicItemDTO() {
    }

    public long getMusicId() {
        return musicId;
    }

    public void setMusicId(long musicId) {
        this.musicId = musicId;
    }

    public String getMusicTitle() {
        return musicTitle;
    }

    public void setMusicTitle(String musicTitle) {
        this.musicTitle = musicTitle;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void bindLibraryItem(Cursor mCursor) {

        setMusicId(mCursor.getLong(mCursor.getColumnIndex(MediaStore.Audio.AudioColumns._ID)));
        setMusicTitle(mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)));
        setArtistName(mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)));
        setAlbumId(mCursor.getLong(mCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)));
        setDuration(mCursor.getLong(mCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)));

    }
}
