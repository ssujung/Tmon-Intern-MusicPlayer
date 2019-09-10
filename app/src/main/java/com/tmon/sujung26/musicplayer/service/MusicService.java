package com.tmon.sujung26.musicplayer.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.tmon.sujung26.musicplayer.R;
import com.tmon.sujung26.musicplayer.broadcast.BroadcastActions;
import com.tmon.sujung26.musicplayer.database.MusicItemDTO;
import com.tmon.sujung26.musicplayer.database.SharedPreferenceInfo;
import com.tmon.sujung26.musicplayer.library.MusicLibraryActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmResults;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = MusicService.class.getSimpleName();
    private final IBinder iBinder = new LocalBinder();
    private static final int NOTIFICATION_ID = 101;
    private MediaPlayer mediaPlayer;
    private int resumePosition;
    private AudioManager audioManager;
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    private RealmResults<MusicItemDTO> playList;
    private int audioIndex = -1;
    private MusicItemDTO activeAudio; //현재 재생중인 곡

    public static final String ACTION_PLAY = "com.tmon.sujung26.musicplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.tmon.sujung26.musicplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.tmon.sujung26.musicplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.tmon.sujung26.musicplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "com.tmon.sujung26.musicplayer.ACTION_STOP";

    private static final int PLAY = 0;
    private static final int PAUSE = 1;
    private static final int STOP = 2;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    private Notification notification;

    private Realm realm;
    private Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
    private boolean shuffle=false;
    private boolean repeat=false;
    private Random random;

    private SharedPreferenceInfo sharedPreferenceInfo;

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        random = new Random();

        realm = Realm.getDefaultInstance();

        callStateListener();                // 전화 왔을 때
        registerBecomingNoisyReceiver();    // 이어폰 뺐을 때
        registerPlayNewAudio();             // 새로 다시 음악 재생 broadcast 왔을 때
        registerStartPlay();                // 앱 실행 후, 처음으로 노래 시작할 때
        registerDeleteNotification();       // notification swipe으로 제거할 때
        registerMusicOrderChanged();        // 재생중인 곡의 순서가 drag & drop으로 인해 바꼈을 경우
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service -- onBind");
        return iBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        playList = realm.where(MusicItemDTO.class).findAll().sort("index");

        if (!requestAudioFocus()) {
            stopSelf();
        }
        if (mediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
        }
        handleIncomingActions(intent);
        return START_STICKY;
    }

    /**
     * Notification 버튼 callback을 위해 init
     * @throws RemoteException
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        updateMetaData();

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlaybackStatus.PLAYING);
                startForeground(NOTIFICATION_ID, notification);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
                stopForeground(false);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
                startForeground(NOTIFICATION_ID, notification);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
                startForeground(NOTIFICATION_ID, notification);
            }

            @Override
            public void onStop() {
                super.onStop();
                stopMedia();
                buildNotification(PlaybackStatus.PAUSED);
                stopForeground(false);
            }

            @Override
            public void onSeekTo(long position) {
                Log.d(TAG, "onSeekTo:" + position);
                seek((int) position);
            }
        });
    }

    /**
     * MediaSession의 Metadata setting
     */
    private void updateMetaData() {
        if (activeAudio != null) {
            String id = String.valueOf(activeAudio.getMusicId());
            String title = activeAudio.getMusicTitle();
            String artist = activeAudio.getArtistName();
            long albumId = activeAudio.getAlbumId();
            long duration = activeAudio.getDuration() * 1000;

            Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, albumId);
            Uri musicRealmURI = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, activeAudio.getMusicId());
            String source = musicRealmURI.toString();

            InputStream inputStream;
            Bitmap albumArt = null;
            try {
                inputStream = getApplicationContext().getContentResolver().openInputStream(thumbnailUri);
                albumArt = BitmapFactory.decodeStream(inputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, source)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    .build());
        }
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
    }

    public MusicItemDTO getActiveMusicItem() {
        if (activeAudio != null) {
            return activeAudio;
        } else {
            Log.e(TAG, "No Active Music");
            return null;
        }
    }

    public int getActiveMusicIndex() {
        if(audioIndex != -1) {
            return audioIndex;
        } else {
            Log.e(TAG, "No Active Music Index");
            return -1;
        }
    }

    public int getResumePosition() {
        return resumePosition;
    }

    public void setResumePosition(int resumePosition) {
        this.resumePosition = resumePosition;
    }

    public void setActiveMusicIndex(int index) {
        audioIndex = index;
        Log.i(TAG, "setActiveMusicIndex : " + audioIndex);
    }

    public void skipToNext() {
        setShuffle();
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
        setPlay();
    }

    public void skipToPrevious() {
        if (audioIndex == 0) {
            audioIndex = playList.size() - 1;
            activeAudio = playList.get(audioIndex);

        } else {
            activeAudio = playList.get(--audioIndex);
        }
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
        setPlay();
    }

    public void playMedia() {
        mHandler.removeCallbacks(mDelayedStopRunnable);
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();

            MusicItemDTO activeMusic = getActiveMusicItem();
            long musicId = activeMusic.getMusicId();
            int activeIndex = getActiveMusicIndex();

            Log.i(TAG, "Active Music : " + activeIndex + " - " + activeMusic.getMusicTitle());

            sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
            sharedPreferenceInfo.savePrevMusicId(musicId);
            sharedPreferenceInfo.savePlayState(PLAY);

            buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
            sendBroadcast(new Intent(BroadcastActions.PLAY_STATE));
        }
    }

    public void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();

            sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
            sharedPreferenceInfo.savePlayState(STOP);

            buildNotification(PlaybackStatus.PAUSED);
            stopForeground(false);
            sendBroadcast(new Intent(BroadcastActions.STOPPED_STATE));
        }
    }

    public void pauseMedia() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            setResumePosition(mediaPlayer.getCurrentPosition());

            sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
            sharedPreferenceInfo.savePlayState(PAUSE);
            sharedPreferenceInfo.savePrevMusicId(activeAudio.getMusicId());

            buildNotification(PlaybackStatus.PAUSED);
            stopForeground(false);
            sendBroadcast(new Intent(BroadcastActions.PAUSED_STATE));
        }
    }

    public void resumeMedia() {
        mHandler.removeCallbacks(mDelayedStopRunnable);
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(getResumePosition());
            mediaPlayer.start();

            sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
            sharedPreferenceInfo.savePlayState(PLAY);

            buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
            sendBroadcast(new Intent(BroadcastActions.RESUME_STATE));
        } else {
            initMediaPlayer();
            setPlay();
        }
    }

    public int getMusicPosition(){
        return mediaPlayer.getCurrentPosition();
    }

    public int getMusicDuration(){
        return mediaPlayer.getDuration();
    }

    public boolean musicIsPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void seek(int position){
        mediaPlayer.seekTo(position);
    }

    private Handler mHandler = new Handler();
    private Runnable mDelayedStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopMedia();
        }
    };

    @Override
    public void onAudioFocusChange(int focusChange) {
        if(focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            Log.i(TAG, "AUDIOFOCUS_GAIN");
            if(!mediaPlayer.isPlaying()) {
                resumeMedia();
                mediaPlayer.setVolume(1.0f, 1.0f);
            }
        } else if(focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            Log.i(TAG, "AUDIOFOCUS_LOSS");
            if(mediaPlayer.isPlaying()) {
                pauseMedia();
                mHandler.postDelayed(mDelayedStopRunnable, TimeUnit.SECONDS.toMillis(30));
            }
        } else if(focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
            if(mediaPlayer.isPlaying()) {
                pauseMedia();
            }
        } else if(focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
            if(mediaPlayer.isPlaying()) {
                mediaPlayer.setVolume(0.1f, 0.1f);
            }
        }
    }

    public void setShuffle() {
        Log.i(TAG, "setShuffle : " + audioIndex);
        shuffle = sharedPreferenceInfo.loadShuffleState();
        if(shuffle) {
            Log.i(TAG, "Shuffle True");
            int newSongPos = audioIndex;
            while(newSongPos == audioIndex) {
                newSongPos = random.nextInt(playList.size());
            }
            audioIndex = newSongPos;

            activeAudio = playList.get(audioIndex);
        } else {
            Log.i(TAG, "Shuffle False");
            if (audioIndex == playList.size() - 1) {
                audioIndex = 0;
                activeAudio = playList.get(audioIndex);
            } else {
                activeAudio = playList.get(++audioIndex);
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        repeat = sharedPreferenceInfo.loadRepeatState();
        if(repeat) {
            setShuffle();
            stopMedia();
            mp.reset();
            initMediaPlayer();
            updateMetaData();
            setPlay();
            buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
            sendBroadcast(new Intent(BroadcastActions.PLAY_STATE));
        } else {
            if (audioIndex == playList.size() - 1) {
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
                sharedPreferenceInfo.savePlayState(STOP);
                sendBroadcast(new Intent(BroadcastActions.TRACK_END));
                buildNotification(PlaybackStatus.PAUSED);
                stopForeground(false);
            } else {
                setShuffle();
                stopMedia();
                mp.reset();
                initMediaPlayer();
                updateMetaData();
                setPlay();
                buildNotification(PlaybackStatus.PLAYING);
                startForeground(NOTIFICATION_ID, notification);
                sendBroadcast(new Intent(BroadcastActions.PLAY_STATE));
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if(what == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
            Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
        } else if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
        } else if(what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
            Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
        }
        sendBroadcast(new Intent(BroadcastActions.PAUSED_STATE));
        return false;
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playMedia();
    }

    public void setPlay() {
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            if(activeAudio != null) {
                Uri musicUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, activeAudio.getMusicId());
                mediaPlayer.setDataSource(getApplicationContext(), musicUri);
            } else {
                Log.e(TAG, "no activeAudio");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.prepareAsync();
    }

    /**
     * 서비스 시작 후, 처음으로 재생할 경우
     */
    private BroadcastReceiver startPlay = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Service -- startPlay");

            audioIndex = intent.getExtras().getInt("audioIndex");
            if (audioIndex != -1 && audioIndex < playList.size()) {
                activeAudio = playList.get(audioIndex);
            } else {
                Log.e(TAG, "cannot insert index to activeAudio");
            }

            initMediaPlayer();
            updateMetaData();
            setPlay();
            buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
        }
    };

    private void registerStartPlay() {
        IntentFilter filter = new IntentFilter(BroadcastActions.START_PLAY);
        registerReceiver(startPlay, filter);
    }

    /**
     * 다른 곡을 선택했을 경우
     */
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Service -- playNewAudio");

            audioIndex = intent.getExtras().getInt("audioIndex", -1);
            if (audioIndex != -1 && audioIndex < playList.size()) {
                activeAudio = playList.get(audioIndex);

            } else {
                Log.e(TAG, "cannot insert index to activeAudio");
            }

            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            setPlay();
            buildNotification(PlaybackStatus.PLAYING);
            startForeground(NOTIFICATION_ID, notification);
        }
    };

    private void registerPlayNewAudio() {
        IntentFilter filter = new IntentFilter(BroadcastActions.PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    /**
     * 재생 목록에 있는 곡의 순서가 Drag & Drop으로 바뀌었을 경우
     */
    private BroadcastReceiver musicOrderChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            MusicItemDTO result = realm.where(MusicItemDTO.class).equalTo("musicId", activeAudio.getMusicId()).findFirst();
            audioIndex = result.getIndex();

            Log.i(TAG, "order changed : " + audioIndex);

        }
    };

    private void registerMusicOrderChanged() {
        IntentFilter filter = new IntentFilter(BroadcastActions.MUSIC_ORDER_CHANGED);
        registerReceiver(musicOrderChanged, filter);
    }

    /**
     * Notification이 Swipe로 인해 제거되었을 경우
     */
    private BroadcastReceiver deleteNotification = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BroadcastActions.DELETE_NOTIFICATION)) {
                SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
                boolean destroy = sharedPreferenceInfo.loadAppDestroyed();
                if(destroy) {
                    stopSelf();
                    Log.e(TAG, "Stop Service");
                } else {
                    removeNotification();
                    Log.e(TAG, "not destroyed");
                }
            }

        }
    };

    private void registerDeleteNotification() {
        IntentFilter filter = new IntentFilter(BroadcastActions.DELETE_NOTIFICATION);
        registerReceiver(deleteNotification, filter);
    }

    /**
     * 이어폰을 제거했을 경우
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
            stopForeground(false);
        }
    };

    private void registerBecomingNoisyReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * 전화 왔을 경우
     */
    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void buildNotification(PlaybackStatus playbackStatus) {

        int notificationAction;
        PendingIntent play_pauseAction = null;
        boolean ongoing = false;
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_ui);

        InputStream inputStream;
        Bitmap thumbnail = null;
        try {
            Uri thumbnailUri = ContentUris.withAppendedId(artworkUri, activeAudio.getAlbumId());
            inputStream = getApplicationContext().getContentResolver().openInputStream(thumbnailUri);
            thumbnail = BitmapFactory.decodeStream(inputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = R.mipmap.pause_icon;
            play_pauseAction = playbackAction(1);
            remoteViews.setImageViewResource(R.id.noti_go_play_pause_btn, notificationAction);
            ongoing = true;
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = R.mipmap.play_icon;
            play_pauseAction = playbackAction(0);
            remoteViews.setImageViewResource(R.id.noti_go_play_pause_btn, notificationAction);
            ongoing = false;
        }

        remoteViews.setTextViewText(R.id.noti_app_title, getApplicationContext().getString(R.string.app_name));
        remoteViews.setTextViewText(R.id.noti_music_title, activeAudio.getMusicTitle());
        remoteViews.setTextViewText(R.id.noti_artist_name, activeAudio.getArtistName());
        remoteViews.setOnClickPendingIntent(R.id.noti_go_prev_btn, playbackAction(3));
        remoteViews.setOnClickPendingIntent(R.id.noti_go_play_pause_btn, play_pauseAction);
        remoteViews.setOnClickPendingIntent(R.id.noti_go_next_btn, playbackAction(2));
        remoteViews.setImageViewResource(R.id.noti_icon, R.drawable.music_note_black);
        remoteViews.setImageViewBitmap(R.id.noti_thumbnail, thumbnail);

        Intent openAppIntent = new Intent(getApplicationContext(), MusicLibraryActivity.class);
        openAppIntent.setAction(Intent.ACTION_MAIN);
        openAppIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent openAppPIntent = PendingIntent.getActivity(getApplicationContext(), 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent deleteNotiIntent = new Intent(BroadcastActions.DELETE_NOTIFICATION);
        PendingIntent deleteNotiPIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, deleteNotiIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                .setContent(remoteViews)
                .setSmallIcon(R.drawable.music_note_white)
                .setLargeIcon(thumbnail)
                .setAutoCancel(false)
                .setOngoing(ongoing)
                .setContentIntent(openAppPIntent)
                .setDeleteIntent(deleteNotiPIntent)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        notification = notificationBuilder.build();

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification);

        Log.i(TAG, "buildNotification = " + activeAudio);
    }

    /**
     * Notification에서 실행하는 click event Intent
     * @param actionNumber
     * @return
     */
    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MusicService.class);
        switch (actionNumber) {
            case 0:
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    /**
     * Notification으로 부터 오는 Action들을 handling
     * @param playbackAction
     */
    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service -- onUnbind");
        mediaSession.release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service -- onDestroy");
        super.onDestroy();
        if(realm != null) {
            realm.close();
        }
        if(mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        removeAudioFocus();
        mHandler.removeCallbacks(mDelayedStopRunnable);

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        SharedPreferenceInfo sharedPreferenceInfo = new SharedPreferenceInfo(getApplicationContext());
        sharedPreferenceInfo.savePlayState(STOP);
        sharedPreferenceInfo.saveActiveMusicIndex(getActiveMusicIndex());

        removeNotification();

        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);
        unregisterReceiver(startPlay);
        unregisterReceiver(deleteNotification);
        unregisterReceiver(musicOrderChanged);

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Service -- onTaskRemoved");
        super.onTaskRemoved(rootIntent);

        if (!musicIsPlaying()) {
            stopSelf();
            mediaPlayer.release();
            mediaPlayer = null;
        }

    }
}
