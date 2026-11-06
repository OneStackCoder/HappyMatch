package com.happymatch.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/**
 * 短音效播放器：基于 SoundPool，适合点击、消除、提示等短促音效。
 */
public class SoundPlayer {

    private static final int MAX_STREAMS = 8;

    private SoundPool soundPool;
    private final Map<Integer, Integer> soundIds = new HashMap<>();

    public SoundPlayer() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build();
    }

    /** 加载音效资源，key 为业务层使用的音效标识 */
    public void load(Context context, int resId, int key) {
        if (soundPool == null) {
            return;
        }
        soundIds.put(key, soundPool.load(context, resId, 1));
    }

    /** 播放音效；资源尚未加载完成时静默忽略 */
    public void play(int key) {
        if (soundPool == null) {
            return;
        }
        Integer soundId = soundIds.get(key);
        if (soundId == null || soundId == 0) {
            return;
        }
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    public void pauseAll() {
        if (soundPool != null) {
            soundPool.autoPause();
        }
    }

    public void resumeAll() {
        if (soundPool != null) {
            soundPool.autoResume();
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
    }
}
