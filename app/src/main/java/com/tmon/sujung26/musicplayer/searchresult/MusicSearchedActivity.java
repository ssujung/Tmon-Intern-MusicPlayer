package com.tmon.sujung26.musicplayer.searchresult;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.service.MusicService;
import com.tmon.sujung26.musicplayer.utility.FragmentToActivity;
import com.tmon.sujung26.musicplayer.utility.OnBackPressedListener;

public class MusicSearchedActivity extends AppCompatActivity implements FragmentToActivity {

    private static final String TAG = MusicSearchedActivity.class.getSimpleName();
    private String searchMusic = "";
    private int fragmentKind;
    private static final int LIST = 0;
    private static final int TILE = 1;

    boolean serviceBound = false;
    boolean secondPlay = false;
    int playState;
    private Menu menu;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.music_search_activity);

        Intent intent = getIntent();

        if(intent != null) {
            searchMusic = intent.getExtras().getString("search");
            Bundle bundle = intent.getExtras().getBundle("fragment");
            if(bundle != null) {
                fragmentKind = bundle.getInt("fragment");
            }
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.search_toolbar);
        toolbar.setTitle("'" + searchMusic + "' " + getString(R.string.search_result_toolbar_title));
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        Intent playerIntent = new Intent(this, MusicService.class);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        Bundle bundle = new Bundle();
        LibrarySearchedFragment librarySearchedFragment = new LibrarySearchedFragment();
        bundle.putInt("fragment", fragmentKind);
        bundle.putString("search", searchMusic);
        librarySearchedFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().add(R.id.search_fragment_place, librarySearchedFragment, "LIBRARY_SEARCHED_FRAGMENT").commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
        playState = sharedPreferenceInfo.loadPlayState();

        Bundle bundle = new Bundle();
        SearchedPlayFragment searchedPlayFragment = new SearchedPlayFragment();
        bundle.putInt("play_state", playState);
        searchedPlayFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().add(R.id.drag_play_fragment_place_search, searchedPlayFragment, "SEARCHED_PLAY_FRAGMENT").commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            MusicService player = binder.getService();
            serviceBound = true;
            Log.e(TAG, "Service Bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void playAudio(int audioIndex) {
        if (serviceBound) {
            if (!secondPlay) {
                secondPlay = true;
                Intent broadcastIntent = new Intent(BroadcastActions.START_PLAY);
                broadcastIntent.putExtra("audioIndex", audioIndex);
                sendBroadcast(broadcastIntent);
            } else {
                secondPlay = false;
                Intent broadcastIntent = new Intent(BroadcastActions.PLAY_NEW_AUDIO);
                broadcastIntent.putExtra("audioIndex", audioIndex);
                sendBroadcast(broadcastIntent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        this.menu = menu;

        menu.findItem(R.id.action_search).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == android.R.id.home) {
            this.finish();
        }else if (id == R.id.action_tile_view) {
            menu.findItem(R.id.action_tile_view).setVisible(false);
            menu.findItem(R.id.action_list_view).setVisible(true);

            fragmentKind = TILE;
            LibrarySearchedFragment librarySearchedFragment = (LibrarySearchedFragment) getSupportFragmentManager().findFragmentByTag("LIBRARY_SEARCHED_FRAGMENT");
            if (librarySearchedFragment != null) {
                librarySearchedFragment.setLayoutType(fragmentKind);
            }

            return true;
        } else if (id == R.id.action_list_view) {
            menu.findItem(R.id.action_tile_view).setVisible(true);
            menu.findItem(R.id.action_list_view).setVisible(false);

            fragmentKind = LIST;
            LibrarySearchedFragment librarySearchedFragment = (LibrarySearchedFragment) getSupportFragmentManager().findFragmentByTag("LIBRARY_SEARCHED_FRAGMENT");
            if (librarySearchedFragment != null) {
                librarySearchedFragment.setLayoutType(fragmentKind);
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("SEARCHED_PLAY_FRAGMENT");
        if(fragment instanceof OnBackPressedListener) {
            ((OnBackPressedListener)fragment).onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(serviceBound) {
            unbindService((serviceConnection));
        }
    }

    @Override
    public void sendPosition(int position, String title) {
        playAudio(position);
    }

    @Override
    public void hideSlidingPaneLayout() {
        SearchedPlayFragment playFragment = (SearchedPlayFragment) getSupportFragmentManager().findFragmentByTag("SEARCHED_PLAY_FRAGMENT");
        if(playFragment != null) {
            playFragment.removeHandler();
            playFragment.slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }
    }


}