package com.tmon.sujung26.musicplayer.database;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferenceInfo {

    public static final String TAG = SharedPreferenceInfo.class.getSimpleName();
    private static final String MUSIC_INFO = "com.tmon.sujung26.musicplayer.MUSIC_INFO";
    private Context context;

    public SharedPreferenceInfo(Context context) {
        this.context = context;
    }

    /**
     * 현재 재생중인 곡의 index 저장
     * @param activeIndex
     */
    public void saveActiveMusicIndex(int activeIndex) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putInt("ACVITE_MUSIC_INDEX", activeIndex);
        editor.apply();
    }

    public int loadActiveAudioIndex() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getInt("ACVITE_MUSIC_INDEX", -1);
    }


    /**
     * 다음 곡으로 넘어갈 때 현재 재생중이던 곡의 ID를 저장 -> equalizer stop해야 하기 때문
     * @param musicId
     */
    public void savePrevMusicId(long musicId) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putLong("PREV_MUSIC_ID", musicId);
        editor.apply();
    }

    public long loadPrevMusicId() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getLong("PREV_MUSIC_ID", -1);
    }

    /**
     * 재생 상태 저장(PLAY, PAUSE, STOP)
     * @param playState
     */
    public void savePlayState(int  playState) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putInt("PLAY_STATE", playState);
        editor.apply();
    }

    public int loadPlayState() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getInt("PLAY_STATE", 2);
    }


    /**
     * shuffle 상태 저장
     * @param shuffleState
     */
    public void saveShuffleState(boolean shuffleState) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putBoolean("SHUFFLE_STATE", shuffleState);
        editor.apply();
    }

    public boolean loadShuffleState() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getBoolean("SHUFFLE_STATE", false);
    }


    /**
     * 전곡 반복 상테 저장
     * @param repeatState
     */
    public void saveRepeatState(boolean repeatState) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putBoolean("REPEAT_STATE", repeatState);
        editor.apply();
    }

    public boolean loadRepeatState() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getBoolean("REPEAT_STATE", false);
    }


    /**
     * App이 destroy되고 Notification이 swipe로 인해 제거되었을 경우 service stop해주기 위해 destroy 상태 저장
     * @param destroy
     */
    public void saveAppDestroyed(boolean destroy) {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putBoolean("APP_DESTROYED", destroy);
        editor.apply();
    }

    public boolean loadAppDestroyed() {
        SharedPreferences preference = context.getSharedPreferences(MUSIC_INFO, Context.MODE_PRIVATE);

        return preference.getBoolean("APP_DESTROYED", false);
    }
}