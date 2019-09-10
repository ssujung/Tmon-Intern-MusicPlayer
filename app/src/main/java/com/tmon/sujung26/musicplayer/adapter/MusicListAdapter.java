package com.tmon.sujung26.musicplayer.adapter;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.utility.PlayListItemTouchHelperAdapter;
import com.tmon.sujung26.musicplayer.utility.PlayListItemTouchHelperViewHolder;
import com.tmon.sujung26.musicplayer.utility.PlayListOnStartDragListener;
import com.tmon.sujung26.musicplayer.utility.RecyclerViewItemClickListener;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmResults;

public class MusicListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements PlayListItemTouchHelperAdapter {

    private static final String TAG = MusicListAdapter.class.getSimpleName();
    private static Activity activity;
    private RealmResults<MusicItemDTO> playList;
    private static final int PLAYLIST_VIEWHOLDER_ID = 2;
    private RecyclerViewItemClickListener recyclerViewItemClickListener;
    private PlayListOnStartDragListener playListOnStartDragListener;
    private Realm realm;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    private int playState = PAUSE;
    private long musicId = -1;
    private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");

    public MusicListAdapter(Activity activity, RealmResults<MusicItemDTO> playList, PlayListOnStartDragListener playListOnStartDragListener, RecyclerViewItemClickListener recyclerViewItemClickListener) {
        MusicListAdapter.activity = activity;
        this.playList = playList;
        this.playListOnStartDragListener = playListOnStartDragListener;
        this.recyclerViewItemClickListener = recyclerViewItemClickListener;

        realm = Realm.getDefaultInstance();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_item_list, parent, false);
        return new PlayListViewHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final PlayListViewHolder playListViewHolder = (PlayListViewHolder) holder;
        if (playList != null) {
            MusicItemDTO musicItemDTO = playList.get(position);
            playListViewHolder.realmItemOnBindViewer(musicItemDTO);

            if (musicItemDTO.getMusicId() == musicId) {
                if (playState == PLAY) {

                    playListViewHolder.thumbnailImg.setVisibility(View.INVISIBLE);

                    startAnimation(playListViewHolder);

                    playListViewHolder.equalizer1.setVisibility(View.VISIBLE);
                    playListViewHolder.equalizer2.setVisibility(View.VISIBLE);
                    playListViewHolder.equalizer3.setVisibility(View.VISIBLE);

                } else if (playState == PAUSE) {

                    playListViewHolder.thumbnailImg.setVisibility(View.INVISIBLE);

                    stopAnimation(playListViewHolder);

                    playListViewHolder.equalizer1.setVisibility(View.VISIBLE);
                    playListViewHolder.equalizer2.setVisibility(View.VISIBLE);
                    playListViewHolder.equalizer3.setVisibility(View.VISIBLE);

                } else if (playState == STOP) {

                    stopAnimation(playListViewHolder);

                    playListViewHolder.equalizer1.setVisibility(View.INVISIBLE);
                    playListViewHolder.equalizer2.setVisibility(View.INVISIBLE);
                    playListViewHolder.equalizer3.setVisibility(View.INVISIBLE);

                    Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
                    Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(playListViewHolder.thumbnailImg);
                    playListViewHolder.thumbnailImg.setVisibility(View.VISIBLE);
                }
            } else {
                stopAnimation(playListViewHolder);

                playListViewHolder.equalizer1.setVisibility(View.INVISIBLE);
                playListViewHolder.equalizer2.setVisibility(View.INVISIBLE);
                playListViewHolder.equalizer3.setVisibility(View.INVISIBLE);

                Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
                Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(playListViewHolder.thumbnailImg);
                playListViewHolder.thumbnailImg.setVisibility(View.VISIBLE);
            }

        } else {
            Log.e(TAG, "no Playlist Item");
        }

        /**
         * cardview를 click 했을 때 재생하기 위해
         */
        playListViewHolder.cardViewLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = playListViewHolder.getAdapterPosition();
                MusicItemDTO musicItemDTO = playList.get(position);
                String musicTitle = musicItemDTO.getMusicTitle();
                recyclerViewItemClickListener.onRecyclerViewItemClicked(musicTitle, position, position, PLAYLIST_VIEWHOLDER_ID);
            }
        });

        /**
         * Play list Item Drag & Drop
         */
        playListViewHolder.dragHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                    playListOnStartDragListener.onStartDrag(playListViewHolder);
                }
                return false;
            }
        });

        playListViewHolder.moreOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context wrapper = new ContextThemeWrapper(activity, R.style.PopupMenu);
                PopupMenu popupMenu = new PopupMenu(wrapper, v);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.menu_option_play_view, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.delete_from_playlist) { // 재생목록에서 제거
                            final int position = playListViewHolder.getAdapterPosition();
                            MusicItemDTO musicItemDTO = playList.get(position);

                            long realmMusicId = musicItemDTO.getMusicId();
                            MusicItemDTO result = realm.where(MusicItemDTO.class).equalTo("musicId", realmMusicId).findFirst();

                            if (result != null) {
                                long musicId = result.getMusicId();

                                recyclerViewItemClickListener.deleteMusicListClicked(musicId, position);

                            } else {
                                Log.e(TAG, "Cannot delete music from playlist");
                            }
                            return true;
                        } else if (item.getItemId() == R.id.deleteMusic_play) {  // 파일 삭제
                            int position = playListViewHolder.getAdapterPosition();
                            MusicItemDTO musicItemDTO = playList.get(position);

                            final String realmMusicTitle = musicItemDTO.getMusicTitle();
                            final long realmMusicId = musicItemDTO.getMusicId();

                            showDeleteDialog(realmMusicId, realmMusicTitle);

                            return true;
                        } else {
                            return false;
                        }
                    }
                });
                popupMenu.show();
            }
        });
    }

    public void changeImage(long musicId, int playState) {
        this.playState = playState;
        this.musicId = musicId;

        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (playList != null) {
            return playList.size();
        } else {
            return 0;
        }
    }

    @Override
    public long getItemId(int position) {
        if (playList != null) {
            return playList.get(position).getMusicId();
        } else {
            return 0;
        }
    }

    private void startAnimation(PlayListViewHolder playListViewHolder) {
        AnimationDrawable animationDrawable1 = (AnimationDrawable) playListViewHolder.equalizer1.getBackground();
        AnimationDrawable animationDrawable2 = (AnimationDrawable) playListViewHolder.equalizer2.getBackground();
        AnimationDrawable animationDrawable3 = (AnimationDrawable) playListViewHolder.equalizer3.getBackground();

        animationDrawable1.start();
        animationDrawable2.start();
        animationDrawable3.start();
    }

    private void stopAnimation(PlayListViewHolder playListViewHolder) {
        AnimationDrawable animationDrawable1 = (AnimationDrawable) playListViewHolder.equalizer1.getBackground();
        AnimationDrawable animationDrawable2 = (AnimationDrawable) playListViewHolder.equalizer2.getBackground();
        AnimationDrawable animationDrawable3 = (AnimationDrawable) playListViewHolder.equalizer3.getBackground();

        animationDrawable1.stop();
        animationDrawable2.stop();
        animationDrawable3.stop();
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        moveItem(playList.get(fromPosition), fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @Override
    public boolean onDrop(int fromPosition, int toPosition) {
        Log.i(TAG, "onDrop : " + fromPosition + " --> " + toPosition);
        notifyItemChanged(fromPosition);
        notifyItemChanged(toPosition);

        activity.sendBroadcast(new Intent(BroadcastActions.MUSIC_ORDER_CHANGED));

        return true;
    }

    private void moveItem(MusicItemDTO musicItemDTO, final int fromPosition, final int toPosition) {
        final int musicIdx = musicItemDTO.getIndex();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                MusicItemDTO tempItem = realm.where(MusicItemDTO.class).equalTo("index", musicIdx).findFirst();
                RealmResults<MusicItemDTO> results;
                if (fromPosition < toPosition) {
                    results = realm.where(MusicItemDTO.class)
                            .greaterThan("index", fromPosition)
                            .lessThanOrEqualTo("index", toPosition)
                            .findAll();
                    for (int i = 0; i < results.size(); i++) {
                        int changeIndex = results.get(i).getIndex();
                        results.get(i).setIndex(changeIndex - 1);
                    }
                } else {
                    results = realm.where(MusicItemDTO.class)
                            .greaterThanOrEqualTo("index", toPosition)
                            .lessThan("index", fromPosition)
                            .findAll();
                    for (int i = 0; i < results.size(); i++) {
                        int changeIndex = results.get(i).getIndex();
                        results.get(i).setIndex(changeIndex + 1);
                    }
                }
                tempItem.setIndex(toPosition);
                realm.insertOrUpdate(playList);
            }
        });
    }

    public void deleteMusicFromRealm(final int position) {

        String resultTitle = playList.get(position).getMusicTitle();

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                Log.i(TAG, getItemCount()+"개");
                playList.deleteFromRealm(position);
                Log.i(TAG, getItemCount()+"개");
                for (int i = 0; i < getItemCount(); i++) {
                    playList.get(i).setIndex(i);
                }
                realm.insertOrUpdate(playList);
            }
        });

        notifyItemRemoved(position);
        Toast.makeText(activity.getBaseContext(), resultTitle + " 을(를) 재생 목록에서 삭제했습니다.", Toast.LENGTH_SHORT).show();
    }

    public void deleteMusicFromStorage(long musicId) {
        String musicTitle = "";
        String where = "_ID='" + musicId + "'";
        String[] projection = {MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE};
        Cursor cursor = activity.getApplicationContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                where,
                null,
                null);
        int column_index_data;
        int column_index_title;
        if (cursor != null) {
            column_index_data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            column_index_title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            if (cursor.moveToFirst()) {
                String filePath = cursor.getString(column_index_data);
                musicTitle = cursor.getString(column_index_title);

                File file = new File(filePath);
                if (file.delete()) {
                    Log.e(TAG, "file remove = " + file.getName() + ", Success");
                } else {
                    Log.e(TAG, "file remove = " + file.getName() + ", Failed");
                }
            }
        }

        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicId);
        Log.i(TAG, "Uri : " + uri);
        activity.getContentResolver().delete(uri, null, null);
        notifyDataSetChanged();
        Toast.makeText(activity.getApplicationContext(), musicTitle + "이(가) 삭제되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void showDeleteDialog(final long libraryMusicId, final String musicTitle) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, R.style.alertDialog);
        alertDialogBuilder
                .setMessage("'" + musicTitle + "'" + activity.getString(R.string.delete_dialog_msg))
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.dialog_confirm),
                        new DialogInterface.OnClickListener() {
                            public void onClick(
                                    DialogInterface dialog, int id) {

                                MusicItemDTO result = realm.where(MusicItemDTO.class).equalTo("musicId", libraryMusicId).findFirst();

                                if (result != null) {
                                    long musicId = result.getMusicId();
                                    recyclerViewItemClickListener.deleteMusicLibraryClicked(musicId);
                                }

                            }
                        })
                .setNegativeButton(activity.getString(R.string.dialog_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(
                                    DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        realm.close();
    }

    private static class PlayListViewHolder extends RecyclerView.ViewHolder implements PlayListItemTouchHelperViewHolder {
        private ImageView dragHandle;
        private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        private ImageView thumbnailImg;
        private TextView musicTitle;
        private TextView artistName;
        private ImageButton moreOption;
        private View cardViewLayout;
        private ImageView equalizer1;
        private ImageView equalizer2;
        private ImageView equalizer3;

        PlayListViewHolder(View v) {
            super(v);
            dragHandle = (ImageView) v.findViewById(R.id.drag_handle);
            thumbnailImg = (ImageView) v.findViewById(R.id.thumbnail_img);
            musicTitle = (TextView) v.findViewById(R.id.music_title);
            artistName = (TextView) v.findViewById(R.id.artist_name);
            moreOption = (ImageButton) v.findViewById(R.id.more_option);
            cardViewLayout = v.findViewById(R.id.music_list_cardView);
            equalizer1 = (ImageView) v.findViewById(R.id.equalizer1);
            equalizer2 = (ImageView) v.findViewById(R.id.equalizer2);
            equalizer3 = (ImageView) v.findViewById(R.id.equalizer3);
        }

        void realmItemOnBindViewer(MusicItemDTO musicItemDTO) {
            musicTitle.setText(musicItemDTO.getMusicTitle());
            if (musicItemDTO.getArtistName().equals("<unknown>")) {
                artistName.setText(activity.getBaseContext().getString(R.string.unknown_artist));
            } else {
                artistName.setText(musicItemDTO.getArtistName());
            }
            Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
            Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(thumbnailImg);
        }

        @Override
        public void onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY);
        }

        @Override
        public void onItemClear() {
            itemView.setBackgroundColor(0);
        }
    }
}

