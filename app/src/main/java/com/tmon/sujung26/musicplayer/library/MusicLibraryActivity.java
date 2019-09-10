package com.tmon.sujung26.musicplayer.library;

import android.Manifest;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.searchresult.MusicSearchedActivity;
import com.tmon.sujung26.musicplayer.service.MusicService;
import com.tmon.sujung26.musicplayer.utility.FragmentToActivity;
import com.tmon.sujung26.musicplayer.utility.OnBackPressedListener;

import java.util.ArrayList;
import java.util.HashSet;

import static android.os.Build.VERSION_CODES.M;

public class MusicLibraryActivity extends AppCompatActivity implements FragmentToActivity {

    private static final String TAG = MusicLibraryActivity.class.getSimpleName();
    private SearchView searchView;
    private int fragmentKind;
    private static final int LIST = 0;
    private static final int TILE = 1;

    private static final int STOP = 2;

    private String getQuery;
    private ArrayList<String> autoSearch;
    private Cursor cursor;

    private MusicService player;
    public boolean serviceBound = false;
    private Menu menu;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.music_library_activity);

        SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
        sharedPreferenceInfo.saveAppDestroyed(false);

        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(playIntent);
        Log.e(TAG, "Service start");

        // Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1000);
            } else {
                getMusicFromMediaStore();
                attachFragment();
            }
        } else {
            getMusicFromMediaStore();
            attachFragment();
        }
    }

    public void attachFragment() {
        // Attach Library List Fragment
        Bundle bundle = new Bundle();
        fragmentKind = LIST;
        LibraryFragment libraryFragment = new LibraryFragment();
        bundle.putInt("fragment", fragmentKind);
        libraryFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_place, libraryFragment, "LIBRARY_FRAGMENT").commit();

        // Attach Play Fragment
        getSupportFragmentManager().beginTransaction().add(R.id.drag_play_fragment_place, new PlayFragment(), "PLAY_FRAGMENT").commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission ok");
            getMusicFromMediaStore();
            attachFragment();
        } else {
            Log.e(TAG, "Permission denied");
            Toast.makeText(MusicLibraryActivity.this, "허용이 거부되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

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

    private void playAudio(int audioIndex) {
        if (serviceBound) {
            if (!player.musicIsPlaying()) {
                Intent broadcastIntent = new Intent(BroadcastActions.START_PLAY);
                broadcastIntent.putExtra("audioIndex", audioIndex);
                sendBroadcast(broadcastIntent);
            } else {
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

        menu.findItem(R.id.action_done).setVisible(false);

        final Menu mMenu = menu;

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mMenu.findItem(R.id.action_list_view).setVisible(false);
                mMenu.findItem(R.id.action_tile_view).setVisible(false);
                mMenu.findItem(R.id.action_done).setVisible(true);
                return false;
            }
        });
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        final SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        searchView.setIconifiedByDefault(true);
        searchView.onActionViewCollapsed();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setQueryHint(getString(R.string.query_hint));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String s) {
                searchView.setIconified(false);
                searchView.onActionViewExpanded();

                Bundle bundle = new Bundle();

                Intent searchMusicIntent = new Intent(MusicLibraryActivity.this, MusicSearchedActivity.class);
                searchMusicIntent.putExtra("search", s);
                bundle.putInt("fragment", fragmentKind);
                searchMusicIntent.putExtra("fragment", bundle);

                startActivity(searchMusicIntent);

                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                SearchView.SearchAutoComplete searchAutoComplete = (SearchView.SearchAutoComplete) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),
                        android.R.layout.simple_dropdown_item_1line, autoSearch);
                searchAutoComplete.setAdapter(adapter);

                final SearchView.SearchAutoComplete finalSearchAuto = searchAutoComplete;
                searchAutoComplete.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        String searchString = (String) parent.getItemAtPosition(position);
                        finalSearchAuto.setText(searchString);
                    }
                });
                getQuery = s;
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (fragmentKind == LIST) {
                    mMenu.findItem(R.id.action_tile_view).setVisible(true);
                } else if (fragmentKind == TILE) {
                    mMenu.findItem(R.id.action_list_view).setVisible(true);
                }
                mMenu.findItem(R.id.action_done).setVisible(false);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Bundle bundle = new Bundle();
        int id = item.getItemId();
        if (id == R.id.action_tile_view) {
            menu.findItem(R.id.action_tile_view).setVisible(false);
            menu.findItem(R.id.action_list_view).setVisible(true);
            fragmentKind = TILE;
            LibraryFragment libraryFragment = (LibraryFragment) getSupportFragmentManager().findFragmentByTag("LIBRARY_FRAGMENT");
            if (libraryFragment != null) {
                libraryFragment.setLayoutType(fragmentKind);
            }

            return true;
        } else if (id == R.id.action_list_view) {
            menu.findItem(R.id.action_tile_view).setVisible(true);
            menu.findItem(R.id.action_list_view).setVisible(false);
            fragmentKind = LIST;
            LibraryFragment libraryFragment = (LibraryFragment) getSupportFragmentManager().findFragmentByTag("LIBRARY_FRAGMENT");
            if (libraryFragment != null) {
                libraryFragment.setLayoutType(fragmentKind);
            }

            return true;
        } else if (id == R.id.action_done) {
            if (getQuery != null && !getQuery.equals("")) {
                Intent searchMusicIntent = new Intent(MusicLibraryActivity.this, MusicSearchedActivity.class);
                searchMusicIntent.putExtra("search", getQuery);
                bundle.putInt("fragment", fragmentKind);
                searchMusicIntent.putExtra("fragment", bundle);
                startActivity(searchMusicIntent);
            } else {
                Log.e(TAG, "no text from search view");
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void getMusicFromMediaStore() {
        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
        };

        cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

        ArrayList<String> tempArr = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                tempArr.add(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)));
                String unKnownArtist = cursor.getString(cursor.getColumnIndex((MediaStore.Audio.Media.ARTIST)));
                if (unKnownArtist.equals("<unknown>")) {
                    tempArr.add(getString(R.string.unknown_artist));
                } else {
                    tempArr.add(cursor.getString(cursor.getColumnIndex((MediaStore.Audio.Media.ARTIST))));
                }
            }
            HashSet hashSet = new HashSet(tempArr);
            autoSearch = new ArrayList<>(hashSet);
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("PLAY_FRAGMENT");
        if (fragment instanceof OnBackPressedListener) {
            ((OnBackPressedListener) fragment).onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
        if (cursor != null) {
            cursor.close();
        }
        if (serviceBound) {
            if(player != null) {
                if(!player.musicIsPlaying()) {
                    sharedPreferenceInfo.savePlayState(STOP);
                }
            }
            unbindService((serviceConnection));
        }
        sharedPreferenceInfo.saveAppDestroyed(true);
    }

    @Override
    public void sendPosition(int position, String title) {
        playAudio(position);
    }

    @Override
    public void hideSlidingPaneLayout() {
        PlayFragment playFragment = (PlayFragment) getSupportFragmentManager().findFragmentByTag("PLAY_FRAGMENT");
        if (playFragment != null) {
            playFragment.removeHandler();
            playFragment.slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }
    }
}