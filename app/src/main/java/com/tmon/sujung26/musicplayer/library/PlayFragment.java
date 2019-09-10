package com.tmon.sujung26.musicplayer.library;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.squareup.picasso.Picasso;
import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.playlist.PlayListFragment;
import com.tmon.sujung26.musicplayer.service.MusicService;
import com.tmon.sujung26.musicplayer.utility.OnBackPressedListener;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmResults;

import static com.tmon.sujung26.musicplayer.R.id.shuffle_btn;

public class PlayFragment extends Fragment
        implements View.OnClickListener, OnBackPressedListener, MediaController.MediaPlayerControl {

    private static final String TAG = PlayFragment.class.getSimpleName();
    public SlidingUpPanelLayout slidingPaneLayout;
    private ImageButton playOptionBtn;
    private ToggleButton playlistBtn, repeatBtn, shuffleBtn;
    private PlayListFragment playListFragment;
    private ImageView albumBg, thumbnail;
    private TextView onGoingDuration, totalDuration, musicTitle, artistName;
    private ImageButton playStopBtn, goPlayPauseBtn, goPrevBtn, goNextBtn;
    private SeekBar musicSeekbar;
    private RelativeLayout musicBarRelativeLayout;
    private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
    private int activeIndex = -1;

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    private MusicService player;
    boolean serviceBound = false;
    private boolean playbackPaused = false;
    private int playState;
    Handler mHandler = new Handler();
    private SharedPreferenceInfo sharedPreferenceInfo;
    private Realm realm;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
        activeIndex = sharedPreferenceInfo.loadActiveAudioIndex();
        playState = sharedPreferenceInfo.loadPlayState();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        realm = Realm.getDefaultInstance();

        View view = inflater.inflate(R.layout.play_fragment, container, false);

        musicBarRelativeLayout = (RelativeLayout) view.findViewById(R.id.musicbar_layout);

        // slidingpanelayout header ui
        View musicbar = view.findViewById(R.id.musicbar);
        thumbnail = (ImageView) musicbar.findViewById(R.id.thumbnail_img);
        musicTitle = (TextView) musicbar.findViewById(R.id.music_title);
        artistName = (TextView) musicbar.findViewById(R.id.artist_name);

        albumBg = (ImageView) view.findViewById(R.id.art_album);
        playlistBtn = (ToggleButton) view.findViewById(R.id.show_play_list_btn);
        playOptionBtn = (ImageButton) view.findViewById(R.id.play_option_btn);
        playStopBtn = (ImageButton) view.findViewById(R.id.play_stop_btn);
        repeatBtn = (ToggleButton) view.findViewById(R.id.repeat_btn);
        shuffleBtn = (ToggleButton) view.findViewById(shuffle_btn);

        onGoingDuration = (TextView) view.findViewById(R.id.ongoing_duration);
        totalDuration = (TextView) view.findViewById(R.id.total_duration);
        goPlayPauseBtn = (ImageButton) view.findViewById(R.id.go_play_pause_btn);
        goPrevBtn = (ImageButton) view.findViewById(R.id.go_prev_btn);
        goNextBtn = (ImageButton) view.findViewById(R.id.go_next_btn);
        musicSeekbar = (SeekBar) view.findViewById(R.id.music_seekbar);

        playStopBtn.setOnClickListener(this);
        repeatBtn.setOnClickListener(this);
        shuffleBtn.setOnClickListener(this);
        playlistBtn.setOnClickListener(this);
        playOptionBtn.setOnClickListener(this);
        goPlayPauseBtn.setOnClickListener(this);
        goPrevBtn.setOnClickListener(this);
        goNextBtn.setOnClickListener(this);

        // slidingPaneLayout
        slidingPaneLayout = (SlidingUpPanelLayout) getActivity().findViewById(R.id.sliding_layout);

        if(getPlayList() == null) {
            slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        } else {
            slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }

        slidingPaneLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {}

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
                if (newState.equals(SlidingUpPanelLayout.PanelState.EXPANDED)) {
                    playlistBtn.setVisibility(View.VISIBLE);
                    playOptionBtn.setVisibility(View.VISIBLE);
                    playStopBtn.setVisibility(View.GONE);
                    slidingPaneLayout.setDragView(R.id.play_total_linear_layout);
                } else if (newState.equals(SlidingUpPanelLayout.PanelState.COLLAPSED)) {
                    setBtnStateToCollapse();
                    if (getActivity().getSupportFragmentManager().getBackStackEntryCount() != 0) {
                        getActivity().getSupportFragmentManager().beginTransaction().remove(playListFragment).commit();
                        getActivity().getSupportFragmentManager().popBackStack("PLAYLIST_FRAGMENT", FragmentManager.POP_BACK_STACK_INCLUSIVE);
                        getActivity().getSupportFragmentManager().executePendingTransactions();
                    }
                }
            }
        });

        slidingPaneLayout.setFadeOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        });

        musicbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (slidingPaneLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    setBtnStateToCollapse();
                    slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                } else if (slidingPaneLayout.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    playlistBtn.setVisibility(View.VISIBLE);
                    playOptionBtn.setVisibility(View.VISIBLE);
                    playStopBtn.setVisibility(View.GONE);
                    slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                }
            }
        });

        // music seekbar
        musicSeekbar.setPadding(0, 0, 0, 0);

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
    public void onResume() {
        super.onResume();
        sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
        boolean shuffleState = sharedPreferenceInfo.loadShuffleState();
        boolean repeatState = sharedPreferenceInfo.loadRepeatState();

        setPlayUI(playState);

        if(shuffleState) {
            shuffleBtn.setChecked(true);
            shuffleBtn.setBackgroundResource(R.mipmap.shuffle_icon_orange);
        }
        if(repeatState) {
            repeatBtn.setChecked(true);
            repeatBtn.setBackgroundResource(R.mipmap.repeat_icon_orange);
        }
    }

    public void showDeleteDialog(final long musicId) {
        MusicItemDTO musicItemDTO = realm.where(MusicItemDTO.class).equalTo("musicId", musicId).findFirst();
        String title = musicItemDTO.getMusicTitle();

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.alertDialog);
        alertDialogBuilder
                .setMessage("'" + title + "'" + getActivity().getString(R.string.delete_dialog_msg))
                .setCancelable(false)
                .setPositiveButton(getActivity().getString(R.string.dialog_confirm),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                if (isPlaying()) {
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

                                deleteMusicFromRealm(musicId);
                                deleteMusicFromLibrary(musicId);

                                if(getPlayList() != null) {
                                    playNext();
                                } else {
                                    removeHandler();
                                    slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
                                }
                            }
                        })
                .setNegativeButton(getActivity().getString(R.string.dialog_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(
                                    DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void deleteMusicFromRealm(long musicId) {
        Log.i(TAG, "gg deleteMusicFromRealm");
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

                for(int i=musicIndex; i<playList.size();i++) {
                    int idx = playList.get(i).getIndex();
                    playList.get(i).setIndex(idx-1);
                }
                realm.insertOrUpdate(playList);
            }
        });
        Toast.makeText(getActivity().getBaseContext(), resultTitle + " 을(를) 재생 목록에서 삭제했습니다.", Toast.LENGTH_SHORT).show();
    }

    public void deleteMusicFromLibrary(long musicId) {
        Log.i(TAG, "gg deleteMusicFromLibrary");
        String musicTitle="";
        String where = "_ID='" + musicId + "'";
        String[] projection = {MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE};
        Cursor cursor = getActivity().getApplicationContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
                if(file.delete()) {
                    Log.e(TAG, "file remove = " + file.getName() + ", Success");
                } else {
                    Log.e(TAG, "file remove = " + file.getName() + ", Failed");
                }
            }
        }

        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicId);
        Log.i(TAG, "Uri : " + uri);
        getActivity().getContentResolver().delete(uri, null, null);
        Toast.makeText(getActivity().getApplicationContext(), musicTitle + "이(가) 삭제되었습니다.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.play_stop_btn) {
            if (playStopBtn.isSelected()) {
                playStopBtn.setSelected(false);
                playStopBtn.setBackgroundResource(R.mipmap.play_icon);
                goPlayPauseBtn.setSelected(false);
                goPlayPauseBtn.setImageResource(R.mipmap.play_button);
                pause();
            } else {
                if (player.getActiveMusicIndex() != -1) {
                    playStopBtn.setSelected(true);
                    playStopBtn.setBackgroundResource(R.mipmap.pause_icon);
                    goPlayPauseBtn.setSelected(true);
                    goPlayPauseBtn.setImageResource(R.mipmap.pause_button);
                    start();
                } else {
                    Intent broadcastIntent = new Intent(BroadcastActions.START_PLAY);
                    broadcastIntent.putExtra("audioIndex", activeIndex);
                    getActivity().sendBroadcast(broadcastIntent);
                }
            }

        } else if (v.getId() == R.id.play_option_btn) {
            Context wrapper = new ContextThemeWrapper(getContext(), R.style.PopupMenuActivity);
            PopupMenu popupMenu = new PopupMenu(wrapper, v);
            MenuInflater inflater = popupMenu.getMenuInflater();
            inflater.inflate(R.menu.menu_option_play_view, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == R.id.delete_from_playlist) {

                        long musicId = getMusicItemId();

                        if(isPlaying() && musicId == player.getActiveMusicItem().getMusicId()) {
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

                            deleteMusicFromRealm(musicId);

                            if(getPlayList() != null) {
                                playNext();
                            } else {
                                removeHandler();
                                slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
                            }
                        }
                        return true;
                    } else if (item.getItemId() == R.id.deleteMusic_play) {
                        long musicId = getMusicItemId();     // 현재 재생목록에 있으면서 재생중인 곡
                        if (musicId != -1) {
                            showDeleteDialog(musicId);
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            popupMenu.show();
        } else if (v.getId() == R.id.show_play_list_btn) {
            playListFragment = new PlayListFragment();
            if (playlistBtn.isChecked()) {
                playlistBtn.setBackgroundResource(R.mipmap.playlist_icon_orange);
                albumBg.setVisibility(View.INVISIBLE);
                FragmentTransaction fragmentTransaction = getActivity().getSupportFragmentManager().beginTransaction();
                fragmentTransaction.setCustomAnimations(R.anim.pop_in, R.anim.pop_out, R.anim.pop_in, R.anim.pop_out);
                fragmentTransaction.add(R.id.playlist_frame_place, playListFragment, "PLAYLIST_FRAGMENT").addToBackStack("PLAYLIST_FRAGMENT").commit();
                getActivity().getSupportFragmentManager().executePendingTransactions();
                slidingPaneLayout.setDragView(musicBarRelativeLayout);
                musicSeekbar.setClickable(false);
                musicSeekbar.setFocusable(false);
                musicSeekbar.setEnabled(false);
            } else {
                playlistBtn.setBackgroundResource(R.mipmap.playlist_icon);
                getActivity().getSupportFragmentManager().beginTransaction().remove(playListFragment).commit();
                getActivity().getSupportFragmentManager().popBackStack("PLAYLIST_FRAGMENT", FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getActivity().getSupportFragmentManager().executePendingTransactions();
                albumBg.setVisibility(View.VISIBLE);
                slidingPaneLayout.setDragView(R.id.play_total_linear_layout);
                musicSeekbar.setClickable(true);
                musicSeekbar.setFocusable(true);
                musicSeekbar.setEnabled(true);
            }
        } else if (v.getId() == R.id.repeat_btn) {
            SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
            if (repeatBtn.isChecked()) {
                repeatBtn.setBackgroundResource(R.mipmap.repeat_icon_orange);
                sharedPreferenceInfo.saveRepeatState(true);
            } else {
                repeatBtn.setBackgroundResource(R.mipmap.repeat_icon_white);
                sharedPreferenceInfo.saveRepeatState(false);
            }

        } else if (v.getId() == shuffle_btn) {
            SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
            if (shuffleBtn.isChecked()) {
                shuffleBtn.setBackgroundResource(R.mipmap.shuffle_icon_orange);
                sharedPreferenceInfo.saveShuffleState(true);
            } else {
                shuffleBtn.setBackgroundResource(R.mipmap.shuffle_icon_white);
                sharedPreferenceInfo.saveShuffleState(false);
            }
        } else if (v.getId() == R.id.go_play_pause_btn) {
            if (goPlayPauseBtn.isSelected()) {
                goPlayPauseBtn.setSelected(false);
                goPlayPauseBtn.setImageResource(R.mipmap.play_button);
                playStopBtn.setSelected(false);
                playStopBtn.setBackgroundResource(R.mipmap.play_icon);
                pause();
            } else {
                if (player.getActiveMusicIndex() != -1) {
                    goPlayPauseBtn.setSelected(true);
                    goPlayPauseBtn.setImageResource(R.mipmap.pause_button);
                    playStopBtn.setSelected(true);
                    playStopBtn.setBackgroundResource(R.mipmap.pause_icon);
                    start();
                } else {
                    Intent broadcastIntent = new Intent(BroadcastActions.START_PLAY);
                    broadcastIntent.putExtra("audioIndex", activeIndex);
                    getActivity().sendBroadcast(broadcastIntent);
                }
            }
        } else if (v.getId() == R.id.go_prev_btn) {
            if (player.getActiveMusicIndex() != -1) {
                playPrev();
            }
        } else if (v.getId() == R.id.go_next_btn) {
            if (player.getActiveMusicIndex() != -1) {
                playNext();
            }
        }
    }

    public void setBtnStateToCollapse() {
        playlistBtn.setChecked(false);
        playlistBtn.setBackgroundResource(R.mipmap.playlist_icon);
        playlistBtn.setVisibility(View.GONE);
        if (albumBg.getVisibility() == View.INVISIBLE) {
            albumBg.setVisibility(View.VISIBLE);
        }
        playOptionBtn.setVisibility(View.GONE);
        playOptionBtn.setFocusable(true);
        playStopBtn.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (slidingPaneLayout != null &&
                (slidingPaneLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED || slidingPaneLayout.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED)) {
            setBtnStateToCollapse();
            slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        } else {
            getActivity().finish();
        }
    }

    private long getMusicItemId() {
        return sharedPreferenceInfo.loadPrevMusicId();
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

    private void setBtnUI(int playState) {
        if (playState == PLAY) {
            goPlayPauseBtn.setSelected(true);
            goPlayPauseBtn.setImageResource(R.mipmap.pause_button);
            playStopBtn.setSelected(true);
            playStopBtn.setBackgroundResource(R.mipmap.pause_icon);
        } else if (playState == PAUSE || playState == STOP) {
            goPlayPauseBtn.setSelected(false);
            goPlayPauseBtn.setImageResource(R.mipmap.play_button);
            playStopBtn.setSelected(false);
            playStopBtn.setBackgroundResource(R.mipmap.play_icon);
        }
    }

    private void setPlayUI(final int playState) {

        MusicItemDTO musicItemDTO = getActiveMusic();
        Log.i(TAG, "setPlayUI - " +musicItemDTO);
        if(musicItemDTO == null) {
            removeHandler();
            slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        } else {
            if (slidingPaneLayout.getPanelState().equals(SlidingUpPanelLayout.PanelState.HIDDEN)) {
                slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
            setBtnUI(playState);

            if (musicItemDTO != null) {
                musicTitle.setText(musicItemDTO.getMusicTitle());
                artistName.setText(musicItemDTO.getArtistName());

                long albumId = musicItemDTO.getAlbumId();
                Uri thumbnailUri = Uri.withAppendedPath(artworkUri, albumId + "");
                Picasso.with(getContext()).load(thumbnailUri).fit().error(R.mipmap.empty_thumbnail).into(albumBg);
                Picasso.with(getContext()).load(thumbnailUri).error(R.mipmap.empty_thumbnail).into(thumbnail);

                musicSeekbar.setMax(getDuration());
                musicSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            if(player != null && serviceBound) {
                                player.setResumePosition(progress);
                            }
                            onGoingDuration.setText(convertDuration(getCurrentPosition()));
                            seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                updateProgressBar();

            }
        }
    }

    private String convertDuration(long duration) {
        String convertedDur;
        String secondStr;

        int minutes = (int)(duration % (1000*60*60)) / (1000*60);
        int seconds = (int) ((duration % (1000*60*60)) % (1000*60) / 1000);

        if(seconds < 10) {
            secondStr = "0" + seconds;
        } else {
            secondStr = "" + seconds;
        }
        convertedDur = minutes + ":" + secondStr;

        return convertedDur;
    }

    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }
    public void removeHandler() {mHandler.removeCallbacks(mUpdateTimeTask);}

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            long totalDur = getDuration();
            long currentDur = getCurrentPosition();

            totalDuration.setText(convertDuration(totalDur));
            if(currentDur > totalDur) {
                currentDur = totalDur;
            }
            onGoingDuration.setText(convertDuration(currentDur));
            musicSeekbar.setProgress(getCurrentPosition());

            mHandler.postDelayed(this, 100);
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BroadcastActions.PLAY_STATE)) {
                Log.i(TAG, "play state");
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
                playState = sharedPreferenceInfo.loadPlayState();

                setPlayUI(playState);

            } else if(intent.getAction().equals(BroadcastActions.RESUME_STATE)) {
                Log.i(TAG, "resume state");
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
                playState = sharedPreferenceInfo.loadPlayState();

                setPlayUI(playState);

            } else if (intent.getAction().equals(BroadcastActions.PAUSED_STATE)) {
                Log.i(TAG, "pause state");
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
                playState = sharedPreferenceInfo.loadPlayState();

                removeHandler();
                setBtnUI(playState);

            } else if (intent.getAction().equals(BroadcastActions.STOPPED_STATE)) {
                Log.i(TAG, "stop state");

                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
                playState = sharedPreferenceInfo.loadPlayState();

                removeHandler();
                setBtnUI(playState);
                musicSeekbar.setProgress(0);

            } else if (intent.getAction().equals(BroadcastActions.TRACK_END)) {
                Log.i(TAG, "track end");
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getContext());
                playState = sharedPreferenceInfo.loadPlayState();

                removeHandler();
                setBtnUI(playState);
                musicSeekbar.setProgress(0);

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

    public void unregisterBroadcast(){
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void start() {
        if(playbackPaused) {
            player.resumeMedia();
            playbackPaused = false;
        } else {
            player.playMedia();
        }
    }

    @Override
    public void pause() {
        player.pauseMedia();
        playbackPaused = true;
    }

    @Override
    public int getDuration() {
        if(player != null && serviceBound)
            if(player.musicIsPlaying()) {
                return player.getMusicDuration();
            } else {
                return (int)player.getActiveMusicItem().getDuration();
            }
        else  return -1;
    }

    @Override
    public int getCurrentPosition() {
        if(player != null && serviceBound) {
            return player.getMusicPosition();
        } else return -1;
    }

    public MusicItemDTO getActiveMusic() {
        if(player != null && serviceBound) {
            return player.getActiveMusicItem();
        } else {
            Log.e(TAG, "No Active Music Item");
            removeHandler();
            slidingPaneLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
            return null;
        }
    }

    @Override
    public void seekTo(int pos) {
        player.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        return player != null && serviceBound && player.musicIsPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    private void playNext() {
        player.skipToNext();
    }

    private void playPrev() {
        player.skipToPrevious();
    }

    @Override
    public void onStop() {
        super.onStop();

        if(serviceBound) {
            getActivity().unbindService(serviceConnection);
        }
        unregisterBroadcast();
        removeHandler();
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
    public void onDetach() {
        super.onDetach();
    }

}