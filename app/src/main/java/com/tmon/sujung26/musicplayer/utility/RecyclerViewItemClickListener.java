package com.tmon.sujung26.musicplayer.utility;

public interface RecyclerViewItemClickListener {

    void onRecyclerViewItemClicked(String title, int playlistIndex, int position, int id);

    void deleteMusicListClicked(long musicId, int position);

    void deleteMusicLibraryClicked(long musicId);

}
