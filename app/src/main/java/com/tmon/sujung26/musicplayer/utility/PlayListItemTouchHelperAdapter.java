package com.tmon.sujung26.musicplayer.utility;

public interface PlayListItemTouchHelperAdapter {

    boolean onItemMove(int fromPosition, int toPosition);

    boolean onDrop(int fromPosition, int toPosition);

//    void onItemDismiss(int position);

}
