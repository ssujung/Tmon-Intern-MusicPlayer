package com.tmon.sujung26.musicplayer.utility;

import android.graphics.Canvas;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

public class PlayListItemTouchHelperCallback extends ItemTouchHelper.Callback {
    private final PlayListItemTouchHelperAdapter mAdapter;
    private Integer mFrom = null;
    private Integer mTo = null;
    public static final float ALPHA_FULL = 1.0f;

    public PlayListItemTouchHelperCallback(PlayListItemTouchHelperAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        final int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false;
        }

        if (mFrom == null) {
            mFrom = source.getAdapterPosition();
        }
        mTo = target.getAdapterPosition();

        mAdapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {
//        mAdapter.onItemDismiss(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }


    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
//        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
//            PlayListItemTouchHelperViewHolder itemViewHolder = (PlayListItemTouchHelperViewHolder) viewHolder;
//            itemViewHolder.onItemSelected();
//        }

        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof PlayListItemTouchHelperViewHolder) {
                PlayListItemTouchHelperViewHolder itemViewHolder = (PlayListItemTouchHelperViewHolder) viewHolder;
                itemViewHolder.onItemSelected();
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        if (viewHolder instanceof PlayListItemTouchHelperViewHolder) {
            PlayListItemTouchHelperViewHolder playListItemTouchHelperViewHolder = (PlayListItemTouchHelperViewHolder) viewHolder;
            playListItemTouchHelperViewHolder.onItemClear();
        }

        if (mFrom != null && mTo != null)
            mAdapter.onDrop(mFrom, mTo);

        mFrom = mTo = null;
    }
}
