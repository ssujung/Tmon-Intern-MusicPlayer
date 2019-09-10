package com.tmon.sujung26.musicplayer.adapter;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.utility.RecyclerViewItemClickListener;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmResults;

public class MusicLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = MusicLibraryAdapter.class.getSimpleName();
    private static Activity activity;
    private int fragmentKind;
    private static final int LIST = 0;
    private static final int TILE = 1;
    private static final int MUSICLIST_VIEWHOLDER_ID = 1;
    private RecyclerViewItemClickListener recyclerViewItemClickListener;
    private Realm realm;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    private int playState = PAUSE;
    private long musicId = -1;
    private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");

    private Cursor cursor;

    public MusicLibraryAdapter(Activity activity, int fragmentKind, RecyclerViewItemClickListener recyclerViewItemClickListener) {
        MusicLibraryAdapter.activity = activity;
        this.fragmentKind = fragmentKind;
        this.recyclerViewItemClickListener = recyclerViewItemClickListener;

        realm = Realm.getDefaultInstance();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        if (viewType == LIST) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.library_item_list, parent, false);
            return new MusicViewHolder(v);
        } else if (viewType == TILE) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.library_item_tile, parent, false);
            return new MusicViewHolder(v);
        } else {
            Log.e(TAG, "no fragment kind");
            return null;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final MusicViewHolder musicViewHolder = (MusicViewHolder) holder;
        final MusicItemDTO musicItemDTO = new MusicItemDTO();
        cursor.moveToPosition(position);

        musicItemDTO.bindLibraryItem(cursor);

        if (musicItemDTO.getArtistName().equals("<unknown>")) {
            musicItemDTO.setArtistName(activity.getBaseContext().getString(R.string.unknown_artist));
        }

        musicViewHolder.itemOnBindViewer(musicItemDTO);

        if (holder.getItemViewType() == LIST) {
            if (musicItemDTO.getMusicId() == musicId) {
                if (playState == PLAY) {

                    musicViewHolder.thumbnailImg.setVisibility(View.INVISIBLE);

                    startAnimation(musicViewHolder);

                    musicViewHolder.equalizer1.setVisibility(View.VISIBLE);
                    musicViewHolder.equalizer2.setVisibility(View.VISIBLE);
                    musicViewHolder.equalizer3.setVisibility(View.VISIBLE);

                } else if (playState == PAUSE) {

                    musicViewHolder.thumbnailImg.setVisibility(View.INVISIBLE);

                    stopAnimation(musicViewHolder);

                    musicViewHolder.equalizer1.setVisibility(View.VISIBLE);
                    musicViewHolder.equalizer2.setVisibility(View.VISIBLE);
                    musicViewHolder.equalizer3.setVisibility(View.VISIBLE);

                } else if (playState == STOP) {

                    stopAnimation(musicViewHolder);

                    musicViewHolder.equalizer1.setVisibility(View.INVISIBLE);
                    musicViewHolder.equalizer2.setVisibility(View.INVISIBLE);
                    musicViewHolder.equalizer3.setVisibility(View.INVISIBLE);

                    Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
                    Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(musicViewHolder.thumbnailImg);
                    musicViewHolder.thumbnailImg.setVisibility(View.VISIBLE);

                }
            } else {
                stopAnimation(musicViewHolder);

                musicViewHolder.equalizer1.setVisibility(View.INVISIBLE);
                musicViewHolder.equalizer2.setVisibility(View.INVISIBLE);
                musicViewHolder.equalizer3.setVisibility(View.INVISIBLE);

                Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
                Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(musicViewHolder.thumbnailImg);
                musicViewHolder.thumbnailImg.setVisibility(View.VISIBLE);
            }
        }

        /**
         * cardview를 click 했을 때 재생하기 위해
         */
        musicViewHolder.cardViewLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = musicViewHolder.getAdapterPosition();
                cursor.moveToPosition(position);

                String libraryMusicTitle = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                long libraryMusicId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

                RealmResults<MusicItemDTO> results = realm.where(MusicItemDTO.class).equalTo("musicId", libraryMusicId).findAll();

                if (results.size() == 0) {
                    insertPlayList(cursor);
                    Number indexNum = realm.where(MusicItemDTO.class).max("index");
                    int index = indexNum.intValue();
                    recyclerViewItemClickListener.onRecyclerViewItemClicked(libraryMusicTitle, index, position, MUSICLIST_VIEWHOLDER_ID);
                } else {
                    Log.e(TAG, "Cannot insert duplicated music --> " + results.get(0).getMusicTitle());
                    recyclerViewItemClickListener.onRecyclerViewItemClicked(libraryMusicTitle, results.get(0).getIndex(), position, MUSICLIST_VIEWHOLDER_ID);
                }
            }
        });

        musicViewHolder.moreOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context wrapper = new ContextThemeWrapper(activity, R.style.PopupMenu);
                PopupMenu popupMenu = new PopupMenu(wrapper, v);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.menu_more_option, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.addMusic) {
                            int position = musicViewHolder.getAdapterPosition();
                            cursor.moveToPosition(position);

                            long libraryMusicId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

                            RealmResults<MusicItemDTO> results = realm.where(MusicItemDTO.class).equalTo("musicId", libraryMusicId).findAll();

                            if (results.size() == 0) {
                                insertPlayList(cursor);
                                Toast.makeText(activity.getBaseContext(), "재생 목록에 추가 되었습니다.", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e(TAG, "Cannot insert duplicated music --> " + results.get(0).getMusicTitle());
                                Toast.makeText(activity.getBaseContext(), "중복된 곡이 재생 목록에 이미 있습니다.", Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        } else if (item.getItemId() == R.id.deleteMusic) {
                            int position = musicViewHolder.getAdapterPosition();
                            cursor.moveToPosition(position);

                            long libraryMusicId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
                            String libraryMusicTitle = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));

                            showDeleteDialog(libraryMusicId, libraryMusicTitle);
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

    @Override
    public int getItemCount() {
        if (cursor != null) {
            return cursor.getCount();
        } else {
            return 0;
        }
    }

    @Override
    public long getItemId(int position) {
        if (cursor != null) {
            if (cursor.moveToPosition(position)) {
                return cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (fragmentKind == 0) {
            return LIST;
        } else if (fragmentKind == 1) {
            return TILE;
        } else {
            return 2;
        }
    }

    public void setCursor(Cursor newCursor) {
        cursor = newCursor;
        notifyDataSetChanged();
    }

    private void startAnimation(MusicViewHolder musicViewHolder) {
        AnimationDrawable animationDrawable1 = (AnimationDrawable) musicViewHolder.equalizer1.getBackground();
        AnimationDrawable animationDrawable2 = (AnimationDrawable) musicViewHolder.equalizer2.getBackground();
        AnimationDrawable animationDrawable3 = (AnimationDrawable) musicViewHolder.equalizer3.getBackground();

        animationDrawable1.start();
        animationDrawable2.start();
        animationDrawable3.start();
    }

    private void stopAnimation(MusicViewHolder musicViewHolder) {
        AnimationDrawable animationDrawable1 = (AnimationDrawable) musicViewHolder.equalizer1.getBackground();
        AnimationDrawable animationDrawable2 = (AnimationDrawable) musicViewHolder.equalizer2.getBackground();
        AnimationDrawable animationDrawable3 = (AnimationDrawable) musicViewHolder.equalizer3.getBackground();

        animationDrawable1.stop();
        animationDrawable2.stop();
        animationDrawable3.stop();
    }

    /**
     * Thumbnail의 Animation 동작을 위해
     * @param musicId
     * @param playState
     */
    public void changeImage(long musicId, int playState) {
        this.playState = playState;
        this.musicId = musicId;

        notifyDataSetChanged();
    }

    private void insertPlayList(Cursor cursor) {
        final Cursor getCursor = cursor;
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                Number indexNum = realm.where(MusicItemDTO.class).max("index");
                long musicId = getCursor.getLong(getCursor.getColumnIndex(MediaStore.Audio.Media._ID));
                int index;
                if (indexNum == null) {
                    index = 0;
                } else {
                    index = indexNum.intValue() + 1;
                }
                MusicItemDTO itemDTO = realm.createObject(MusicItemDTO.class, musicId);
                itemDTO.setMusicTitle(getCursor.getString(getCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)));
                itemDTO.setArtistName(getCursor.getString(getCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)));
                itemDTO.setAlbumId(getCursor.getLong(getCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)));
                itemDTO.setDuration(getCursor.getLong(getCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)));
                itemDTO.setIndex(index);
            }
        });
        notifyDataSetChanged();
    }

    public void deleteMusicFromPlayList(long musicId) {
        final MusicItemDTO result = realm.where(MusicItemDTO.class).equalTo("musicId", musicId).findFirst();

        final String resultTitle = result.getMusicTitle();
        final int musicIndex = result.getIndex();
        final RealmResults<MusicItemDTO> playList = realm.where(MusicItemDTO.class).findAll().sort("index");

        Log.i(TAG, "deleteMusic : " + resultTitle + " = " + musicIndex);

        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                Log.e(TAG, musicIndex + " :: " + resultTitle);
                result.deleteFromRealm();

                for(int i=0;i<playList.size();i++) {
                    playList.get(i).setIndex(i);
                }
                realm.insertOrUpdate(playList);
            }
        });
        notifyDataSetChanged();
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
        activity.getApplicationContext().getContentResolver().delete(uri, null, null);
        notifyDataSetChanged();
        Toast.makeText(activity.getApplicationContext(), musicTitle + "이(가) 삭제되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * 파일 삭제할 때 나오는 다이얼로그
     * @param libraryMusicId
     * @param musicTitle
     */
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

                                if (result != null) {        // 재생목록에 있을 경우
                                    long musicId = result.getMusicId();

                                    recyclerViewItemClickListener.deleteMusicLibraryClicked(musicId);

                                } else {                    // 재생목록에 없을 경우
                                    deleteMusicFromStorage(libraryMusicId);
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

    private static class MusicViewHolder extends RecyclerView.ViewHolder {
        private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        private ImageView thumbnailImg;
        private TextView musicTitle;
        private TextView artistName;
        private ImageButton moreOption;
        private View cardViewLayout;
        private ImageView equalizer1;
        private ImageView equalizer2;
        private ImageView equalizer3;

        MusicViewHolder(View v) {
            super(v);
            thumbnailImg = (ImageView) v.findViewById(R.id.thumbnail_img);
            musicTitle = (TextView) v.findViewById(R.id.music_title);
            artistName = (TextView) v.findViewById(R.id.artist_name);
            moreOption = (ImageButton) v.findViewById(R.id.more_option);
            cardViewLayout = v.findViewById(R.id.music_list_cardView);
            equalizer1 = (ImageView) v.findViewById(R.id.equalizer1);
            equalizer2 = (ImageView) v.findViewById(R.id.equalizer2);
            equalizer3 = (ImageView) v.findViewById(R.id.equalizer3);
        }

        void itemOnBindViewer(MusicItemDTO musicItemDTO) {
            musicTitle.setText(musicItemDTO.getMusicTitle());
            artistName.setText(musicItemDTO.getArtistName());
            Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, musicItemDTO.getAlbumId());
            Picasso.with(activity.getBaseContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(thumbnailImg);
        }
    }


}

