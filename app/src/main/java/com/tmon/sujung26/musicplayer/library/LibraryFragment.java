package com.tmon.sujung26.musicplayer.library;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.adapter.MusicLibraryAdapter;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.service.MusicService;
import com.tmon.sujung26.musicplayer.utility.FragmentToActivity;
import com.tmon.sujung26.musicplayer.utility.RecyclerViewItemClickListener;

import io.realm.Realm;
import io.realm.RealmResults;

public class LibraryFragment extends Fragment implements RecyclerViewItemClickListener {

    private static final String TAG = LibraryFragment.class.getSimpleName();
    private MusicLibraryAdapter musicLibraryAdapter;
    private int fragmentKind;
    private TextView emptyList;
    private FragmentToActivity fragmentToActivity;
    private RecyclerView recyclerView;
    private static final int MUSICLIST_VIEWHOLDER_ID = 1;

    private MusicService player;
    public boolean serviceBound = false;

    private static final int LIST = 0;
    private static final int TILE = 1;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    private Cursor cursor;
    private Realm realm;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        context = getActivity();
        fragmentToActivity = (FragmentToActivity) context;

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        if(getArguments() != null) {
            fragmentKind = getArguments().getInt("fragment");
        }

        getMusicListfromMediaStore();

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        realm = Realm.getDefaultInstance();

        View view = inflater.inflate(R.layout.library_fragment, container, false);

        setHasOptionsMenu(true);

        emptyList = (TextView) view.findViewById(R.id.empty_music_list);

        recyclerView = (RecyclerView) view.findViewById(R.id.music_recyclerView);
        musicLibraryAdapter = new MusicLibraryAdapter(getActivity(), fragmentKind, this);
        recyclerView.setHasFixedSize(false);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        recyclerView.setAdapter(musicLibraryAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(fragmentKind == LIST) {
            menu.findItem(R.id.action_list_view).setVisible(false);
            menu.findItem(R.id.action_tile_view).setVisible(true);
        } else if(fragmentKind == TILE) {
            menu.findItem(R.id.action_list_view).setVisible(true);
            menu.findItem(R.id.action_tile_view).setVisible(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent playerIntent = new Intent(getActivity(), MusicService.class);
        getActivity().bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        registerBroadcast();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
        if (sharedPreferenceInfo.loadPlayState() == PLAY) {
            getActivity().sendBroadcast(new Intent(BroadcastActions.PLAY_STATE));
        } else if(sharedPreferenceInfo.loadPlayState() == PAUSE) {
            getActivity().sendBroadcast(new Intent(BroadcastActions.PAUSED_STATE));
        } else if(sharedPreferenceInfo.loadPlayState() == STOP) {
            getActivity().sendBroadcast(new Intent(BroadcastActions.STOPPED_STATE));
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
            Log.e(TAG, "Service Bound");

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());

            if (intent.getAction().equals(BroadcastActions.PLAY_STATE)) {
                Log.i(TAG, "play state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicLibraryAdapter.changeImage(musicId, PLAY);

            } else if(intent.getAction().equals(BroadcastActions.RESUME_STATE)) {
                Log.i(TAG, "resume state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicLibraryAdapter.changeImage(musicId, PLAY);

            } else if (intent.getAction().equals(BroadcastActions.PAUSED_STATE)) {
                Log.i(TAG, "pause state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicLibraryAdapter.changeImage(musicId, PAUSE);

            } else if(intent.getAction().equals(BroadcastActions.STOPPED_STATE)) {
                Log.i(TAG, "stop state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicLibraryAdapter.changeImage(musicId, STOP);
            } else if (intent.getAction().equals(BroadcastActions.TRACK_END)) {
                Log.i(TAG, "track end");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicLibraryAdapter.changeImage(musicId, STOP);

            }
        }
    };

    public void registerBroadcast(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(BroadcastActions.PLAY_STATE);
        filter.addAction(BroadcastActions.PAUSED_STATE);
        filter.addAction(BroadcastActions.RESUME_STATE);
        filter.addAction(BroadcastActions.STOPPED_STATE);
        filter.addAction(BroadcastActions.TRACK_END);
        getActivity().registerReceiver(mBroadcastReceiver, filter);
    }

    public void unregisterBroadcast() {
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    public void setLayoutType(int fragmentKind) {
        this.fragmentKind = fragmentKind;
        if(fragmentKind == LIST) {
            musicLibraryAdapter = new MusicLibraryAdapter(getActivity(), fragmentKind, this);
            musicLibraryAdapter.setCursor(cursor);
            recyclerView.setHasFixedSize(false);
            recyclerView.setDrawingCacheEnabled(true);
            recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(musicLibraryAdapter);

        } else if(fragmentKind == TILE) {
            musicLibraryAdapter = new MusicLibraryAdapter(getActivity(), fragmentKind, this);
            musicLibraryAdapter.setCursor(cursor);
            recyclerView.setHasFixedSize(true);
            recyclerView.setDrawingCacheEnabled(true);
            recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
            recyclerView.setAdapter(musicLibraryAdapter);
        }

    }

    public void getMusicListfromMediaStore() {
        getLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA
                };
                String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
                return new CursorLoader(getActivity(), musicUri, projection, null, null, sortOrder);
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                musicLibraryAdapter.setCursor(null);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                cursor = data;

                musicLibraryAdapter.setCursor(cursor);

                int itemSize = musicLibraryAdapter.getItemCount();
                if (itemSize == 0) {
                    emptyList.setVisibility(View.VISIBLE);
                } else {
                    emptyList.setVisibility(View.GONE);
                    while (cursor.moveToNext()) {
                        Log.i(TAG, cursor.getString(data.getColumnIndex(MediaStore.Audio.Media._ID)) + " :: " + cursor.getString(data.getColumnIndex(MediaStore.Audio.Media.TITLE)) + " : " + cursor.getString(data.getColumnIndex((MediaStore.Audio.Media.ARTIST))));
                    }
                }
            }
        });
    }

    @Override
    public void onRecyclerViewItemClicked(String title, int playlistIndex, int position, int id) {
        if(id == MUSICLIST_VIEWHOLDER_ID) {

            fragmentToActivity.sendPosition(playlistIndex, title);
        }
    }

    @Override
    public void deleteMusicListClicked(long musicId, int position) {

    }

    /**
     * 재생목록에 곡이 존재하는 경우 삭제하기 위한 method
     * @param musicId
     */
    @Override
    public void deleteMusicLibraryClicked(long musicId) {
        Log.i(TAG, "deleteMusicLibraryClicked");
        if (player != null && serviceBound) {
            if (musicId == player.getActiveMusicItem().getMusicId()) {
                // 재생목록에 있는 곡이 재생중일 경우
                if(player.musicIsPlaying()) {
                    player.stopMedia();
                }

                int lastIndex = getPlayList().size() - 1;
                int index = player.getActiveMusicIndex();

                if (getPlayList().get(lastIndex).getMusicId() == musicId) {
                    // 삭제할 곡이 playlist의 가장 마지막에 있는 경우
                    player.setActiveMusicIndex(lastIndex - 1);
                } else {
                    player.setActiveMusicIndex(index - 1);
                }

                musicLibraryAdapter.deleteMusicFromPlayList(musicId);
                musicLibraryAdapter.deleteMusicFromStorage(musicId);

                if(getPlayList() != null) {
                    player.skipToNext();
                } else {
                    fragmentToActivity.hideSlidingPaneLayout();
                }
            } else {
                // 재생목록에 있는 곡이 재생중이 아닐 경우

                musicLibraryAdapter.deleteMusicFromPlayList(musicId);
                musicLibraryAdapter.deleteMusicFromStorage(musicId);

                if(getPlayList() == null) {
                    fragmentToActivity.hideSlidingPaneLayout();
                }
            }
        }
    }

    private RealmResults<MusicItemDTO> getPlayList() {
        RealmResults<MusicItemDTO> playList = realm.where(MusicItemDTO.class).findAll().sort("index");
        if (playList.size() != 0) {
            for (MusicItemDTO m : playList) {
                Log.i(TAG, m.getIndex() + " :: " + m.getMusicTitle() + " :: " + m.getArtistName());
            }
            return playList;
        } else {
            Log.e(TAG, "no Play List Item");
            return null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            getActivity().unbindService((serviceConnection));
        }
        unregisterBroadcast();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        realm.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
