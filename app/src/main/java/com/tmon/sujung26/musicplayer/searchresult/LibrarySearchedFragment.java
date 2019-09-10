package com.tmon.sujung26.musicplayer.searchresult;

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

public class LibrarySearchedFragment extends Fragment implements RecyclerViewItemClickListener {

    private static final String TAG = LibrarySearchedFragment.class.getSimpleName();
    private MusicLibraryAdapter musicLibraryAdapter;
    private int fragmentKind;
    private String searchMusic = "";
    private TextView emptyList;
    private FragmentToActivity fragmentToActivity;
    private RecyclerView recyclerView;
    private static final int MUSICLIST_VIEWHOLDER_ID = 1;
    private Cursor cursor;

    private MusicService player;
    public boolean serviceBound = false;
    private int activePosition;

    private static final int LIST = 0;
    private static final int TILE = 1;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

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
            searchMusic = getArguments().getString("search");
            Log.i(TAG, "search : " + searchMusic);
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
        menu.findItem(R.id.action_done).setVisible(false);
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

                // 다음 곡으로 넘어갔을 때 현재 재생중이던 곡을 저장해야 함 -> equalizer stop해야 하기 때문.
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
                if(searchMusic.equals(getContext().getString(R.string.unknown_artist))) {
                    searchMusic = "<unknown>";
                }
                String where = MediaStore.Audio.Media.TITLE + " = ? OR " + MediaStore.Audio.Media.ARTIST + " = ?";
                String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
                return new CursorLoader(getActivity(), musicUri, projection, where, new String[] { searchMusic, searchMusic }, sortOrder);
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
                }
            }
        });
    }

    @Override
    public void onRecyclerViewItemClicked(String title, int playlistIndex, int position, int id) {
        if(id == MUSICLIST_VIEWHOLDER_ID) {
            activePosition = position;
            fragmentToActivity.sendPosition(playlistIndex, title);
        }
    }

    @Override
    public void deleteMusicListClicked(long musicId, int position) {

    }

    @Override
    public void deleteMusicLibraryClicked(long musicId) {
        if (player != null && serviceBound) {
            if (player.musicIsPlaying() && musicId == player.getActiveMusicItem().getMusicId()) {
                player.stopMedia();
            }

            int lastIndex = getPlayList().size() - 1;
            if (getPlayList().get(lastIndex).getMusicId() == musicId) {
                // 삭제할 곡이 playlist의 가장 마지막에 있는 경우
                player.setActiveMusicIndex(lastIndex - 1);
            } else {
                int index = player.getActiveMusicIndex();
                player.setActiveMusicIndex(index - 1);
            }
            if(!player.musicIsPlaying()) {
                musicLibraryAdapter.deleteMusicFromPlayList(musicId);
                musicLibraryAdapter.deleteMusicFromStorage(musicId);
            }

            if(getPlayList() != null) {
                player.skipToNext();
            } else {
                fragmentToActivity.hideSlidingPaneLayout();
            }
        }
    }

    private RealmResults<MusicItemDTO> getPlayList() {
        Log.e(TAG, "getPlayList");
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

        if(cursor != null) {
            cursor.close();
        }
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
