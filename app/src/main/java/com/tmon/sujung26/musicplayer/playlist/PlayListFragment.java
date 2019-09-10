package com.tmon.sujung26.musicplayer.playlist;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.adapter.MusicListAdapter;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.service.MusicService;
import com.tmon.sujung26.musicplayer.utility.FragmentToActivity;
import com.tmon.sujung26.musicplayer.utility.PlayListItemTouchHelperCallback;
import com.tmon.sujung26.musicplayer.utility.PlayListOnStartDragListener;
import com.tmon.sujung26.musicplayer.utility.RecyclerViewItemClickListener;

import io.realm.Realm;
import io.realm.RealmResults;

public class PlayListFragment extends Fragment implements PlayListOnStartDragListener, RecyclerViewItemClickListener {

    private static final String TAG = PlayListFragment.class.getSimpleName();
    private ItemTouchHelper itemTouchHelper;
    private MusicListAdapter musicListAdapter;
    private static final int PLAYLIST_VIEWHOLDER_ID = 2;
    private FragmentToActivity fragmentToActivity;

    private MusicService player;
    public boolean serviceBound = false;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    private Realm realm;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        realm = Realm.getDefaultInstance();

        View view = inflater.inflate(R.layout.playlist_fragment, container, false);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.playlist_recyclerview);
        musicListAdapter = new MusicListAdapter(getActivity(), getPlayList(), this, this);
        recyclerView.setHasFixedSize(true);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        recyclerView.setAdapter(musicListAdapter);

        ItemTouchHelper.Callback callback = new PlayListItemTouchHelperCallback(musicListAdapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        return view;
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

    @Override
    public void onStart() {
        super.onStart();
        Intent playerIntent = new Intent(getActivity(), MusicService.class);
        getActivity().bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        registerBroadcast();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        context = getActivity();
        fragmentToActivity = (FragmentToActivity) context;

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

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());

            if (intent.getAction().equals(BroadcastActions.PLAY_STATE)) {
                Log.i(TAG, "play state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicListAdapter.changeImage(musicId, PLAY);

            } else if(intent.getAction().equals(BroadcastActions.RESUME_STATE)) {
                Log.i(TAG, "resume state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicListAdapter.changeImage(musicId, PLAY);

            } else if (intent.getAction().equals(BroadcastActions.PAUSED_STATE)) {
                Log.i(TAG, "pause state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicListAdapter.changeImage(musicId, PAUSE);

            } else if(intent.getAction().equals(BroadcastActions.STOPPED_STATE)) {
                Log.i(TAG, "stop state");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicListAdapter.changeImage(musicId, STOP);

            } else if (intent.getAction().equals(BroadcastActions.TRACK_END)) {
                Log.i(TAG, "track end");

                long musicId = sharedPreferenceInfo.loadPrevMusicId();

                musicListAdapter.changeImage(musicId, STOP);

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


    private RealmResults<MusicItemDTO> getPlayList() {
        Log.e(TAG, "getPlayList");
        RealmResults<MusicItemDTO> realmList = realm.where(MusicItemDTO.class).findAll().sort("index");
        if (realmList.size() != 0) {
            for (MusicItemDTO m : realmList) {
                Log.i(TAG, m.getIndex() + " :: " + m.getMusicId() + " :: " + m.getMusicTitle() + " :: " + m.getArtistName());
            }
            return realmList;
        } else {
            Log.e(TAG, "no Play List Item");
            return null;
        }
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onStop() {
        super.onStop();

        if(serviceBound) {
            getActivity().unbindService(serviceConnection);
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

    @Override
    public void onRecyclerViewItemClicked(String title, int playlistIndex, int position, int id) {
        if (id == PLAYLIST_VIEWHOLDER_ID) {
            fragmentToActivity.sendPosition(playlistIndex, title);
        }
    }

    @Override
    public void deleteMusicListClicked(long musicId, int position) {
        Log.i(TAG, "deleteMusicListClicked");
        if (player != null && serviceBound) {
            if (player.musicIsPlaying() && musicId == player.getActiveMusicItem().getMusicId()) {
                player.stopMedia();

                int playListSize = getPlayList().size();
                int lastIndex = playListSize - 1;
                if (playListSize > 1 && getPlayList().get(lastIndex).getMusicId() == musicId) {
                    // 삭제할 곡이 playlist의 가장 마지막에 있는 경우
                    player.setActiveMusicIndex(lastIndex - 1);
                } else {
                    int index = player.getActiveMusicIndex();
                    player.setActiveMusicIndex(index - 1);
                }

                musicListAdapter.deleteMusicFromRealm(position);

                if(musicListAdapter.getItemCount() != 0) {
                    player.skipToNext();
                } else {
                    fragmentToActivity.hideSlidingPaneLayout();
                }

            } else {
                musicListAdapter.deleteMusicFromRealm(position);

                if(musicListAdapter.getItemCount() == 0) {
                    fragmentToActivity.hideSlidingPaneLayout();
                }
            }

        }
    }

    @Override
    public void deleteMusicLibraryClicked(long musicId) {
        if (player != null && serviceBound) {
            if (musicId == player.getActiveMusicItem().getMusicId()) {
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

                musicListAdapter.deleteMusicFromRealm(index);
                musicListAdapter.deleteMusicFromStorage(musicId);

                if (getPlayList().size() != 0) {
                    player.skipToNext();
                } else {
                    fragmentToActivity.hideSlidingPaneLayout();
                }
            } else {
                MusicItemDTO result = realm.where(MusicItemDTO.class).equalTo("musicId", musicId).findFirst();
                musicListAdapter.deleteMusicFromRealm(result.getIndex());
                musicListAdapter.deleteMusicFromStorage(musicId);

                if(getPlayList() == null) {
                    fragmentToActivity.hideSlidingPaneLayout();
                } else {
                    int lastIndex = getPlayList().size() - 1;
                    int index = player.getActiveMusicIndex();
                    if (getPlayList().get(lastIndex).getMusicId() == musicId) {
                        player.setActiveMusicIndex(lastIndex - 1);
                    } else {
                        player.setActiveMusicIndex(index - 1);
                    }
                }
            }
        }
    }


}