package com.happymatch.game;

import android.content.Context;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import com.happymatch.R;
import com.happymatch.audio.SoundPlayer;
import com.happymatch.ui.MatchToast;

/**
 * 游戏流程控制：关卡、倒计时、道具、音效与状态回调。
 */
public class GameView extends BoardView {

    public static final int STATE_WIN = 1;
    public static final int STATE_LOSE = 2;

    /** 第一关总时长（秒） */
    private static final int BASE_TOTAL_TIME = 100;
    /** 每过一关减少的秒数 */
    private static final int TIME_DECREASE_PER_LEVEL = 10;
    /** 总时长下限（秒） */
    private static final int MIN_TOTAL_TIME = 40;
    /** 每种道具的初始数量 */
    private static final int TOOL_TOTAL_NUM = 3;
    /** 提示高亮展示时长（毫秒） */
    private static final long HINT_SHOW_DURATION = 1500L;

    private static final int SFX_CHOOSE = 1;
    private static final int SFX_DISAPPEAR = 2;
    private static final int SFX_WIN = 3;
    private static final int SFX_LOSE = 4;
    private static final int SFX_DISRUPT = 5;
    private static final int SFX_TIP = 6;
    private static final int SFX_ERROR = 7;

    private final SoundPlayer soundPlayer;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer bgm;
    private int level = 1;
    private int totalTime = BASE_TOTAL_TIME;
    private int leftTime = BASE_TOTAL_TIME;
    private int disruptLeftNum = TOOL_TOTAL_NUM;
    private int tipLeftNum = TOOL_TOTAL_NUM;

    private boolean running;
    private boolean paused;

    private OnTimeChangeListener timeChangeListener;
    private OnStateChangeListener stateChangeListener;
    private OnToolsChangeListener toolsChangeListener;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || paused) {
                return;
            }
            leftTime--;
            if (leftTime < 0) {
                leftTime = 0;
            }
            notifyTimeChanged();
            if (leftTime <= 0) {
                finishGame(STATE_LOSE);
                return;
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable clearHintRunnable = new Runnable() {
        @Override
        public void run() {
            clearHint();
            if (running && !paused) {
                setInteractive(true);
            }
        }
    };

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        soundPlayer = new SoundPlayer();
        loadSounds();
    }

    private void loadSounds() {
        Context context = getContext();
        soundPlayer.load(context, R.raw.choose, SFX_CHOOSE);
        soundPlayer.load(context, R.raw.disappear, SFX_DISAPPEAR);
        soundPlayer.load(context, R.raw.win, SFX_WIN);
        soundPlayer.load(context, R.raw.lose, SFX_LOSE);
        soundPlayer.load(context, R.raw.disrupt, SFX_DISRUPT);
        soundPlayer.load(context, R.raw.tip, SFX_TIP);
        soundPlayer.load(context, R.raw.alarm, SFX_ERROR);
    }

    // ----------------------------------------------------------------------------------
    // 对外控制接口
    // ----------------------------------------------------------------------------------

    /** 开始一局新游戏（从第一关开始） */
    public void start() {
        level = 1;
        totalTime = BASE_TOTAL_TIME;
        startLevel();
    }

    /** 重玩当前关卡 */
    public void replay() {
        startLevel();
    }

    /** 进入下一关，时限逐关递减 */
    public void nextLevel() {
        level++;
        totalTime = Math.max(MIN_TOTAL_TIME, totalTime - TIME_DECREASE_PER_LEVEL);
        startLevel();
    }

    /** 暂停计时与背景音乐（Activity 不可见时调用） */
    public void pause() {
        if (!running || paused) {
            return;
        }
        paused = true;
        setInteractive(false);
        timerHandler.removeCallbacks(tickRunnable);
        if (bgm != null && bgm.isPlaying()) {
            bgm.pause();
        }
        soundPlayer.pauseAll();
    }

    /** 恢复计时与背景音乐 */
    public void resume() {
        if (!running || !paused) {
            return;
        }
        paused = false;
        setInteractive(true);
        if (bgm != null) {
            try {
                bgm.start();
            } catch (IllegalStateException ignored) {
                // 播放器尚未准备好时忽略
            }
        }
        soundPlayer.resumeAll();
        timerHandler.postDelayed(tickRunnable, 1000);
    }

    /** 释放所有资源，退出前调用 */
    public void release() {
        running = false;
        paused = false;
        timerHandler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacksAndMessages(null);
        setInteractive(false);
        if (bgm != null) {
            try {
                bgm.release();
            } catch (Exception ignored) {
                // 已释放时忽略
            }
            bgm = null;
        }
        soundPlayer.release();
    }

    /** 洗牌道具 */
    public void useDisrupt() {
        if (!running || paused || isResolving()) {
            return;
        }
        if (disruptLeftNum <= 0) {
            soundPlayer.play(SFX_ERROR);
            notifyDisrupt(OnToolsChangeListener.TOOL_USED_UP);
            return;
        }
        disruptLeftNum--;
        shuffleBoard();
        soundPlayer.play(SFX_DISRUPT);
        notifyDisrupt(OnToolsChangeListener.TOOL_SUCCESS);
    }

    /** 提示道具 */
    public void useTip() {
        if (!running || paused || isResolving()) {
            return;
        }
        if (tipLeftNum <= 0) {
            soundPlayer.play(SFX_ERROR);
            notifyTip(OnToolsChangeListener.TOOL_USED_UP);
            return;
        }
        Point[] hint = findHint();
        if (hint == null) {
            soundPlayer.play(SFX_ERROR);
            notifyTip(OnToolsChangeListener.TOOL_FAIL);
            return;
        }

        tipLeftNum--;
        soundPlayer.play(SFX_TIP);
        showHint(hint[0], hint[1]);
        setInteractive(false);
        uiHandler.removeCallbacks(clearHintRunnable);
        uiHandler.postDelayed(clearHintRunnable, HINT_SHOW_DURATION);
        notifyTip(OnToolsChangeListener.TOOL_SUCCESS);
    }

    // ----------------------------------------------------------------------------------
    // 内部流程
    // ----------------------------------------------------------------------------------

    private void startLevel() {
        uiHandler.removeCallbacks(clearHintRunnable);
        timerHandler.removeCallbacksAndMessages(null);

        newBoard();
        disruptLeftNum = TOOL_TOTAL_NUM;
        tipLeftNum = TOOL_TOTAL_NUM;
        leftTime = totalTime;
        running = true;
        paused = false;
        setInteractive(true);

        notifyTimeChanged();
        notifyDisrupt(OnToolsChangeListener.TOOL_SUCCESS);
        notifyTip(OnToolsChangeListener.TOOL_SUCCESS);

        startBgm();
        timerHandler.postDelayed(tickRunnable, 1000);
    }

    private void finishGame(int state) {
        running = false;
        paused = false;
        setInteractive(false);
        timerHandler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacks(clearHintRunnable);
        clearHint();
        if (bgm != null && bgm.isPlaying()) {
            bgm.pause();
        }
        soundPlayer.play(state == STATE_WIN ? SFX_WIN : SFX_LOSE);
        if (stateChangeListener != null) {
            stateChangeListener.onStateChange(state);
        }
    }

    private void startBgm() {
        if (bgm == null) {
            try {
                bgm = MediaPlayer.create(getContext(), R.raw.game_bg);
                if (bgm != null) {
                    bgm.setLooping(true);
                }
            } catch (Exception ignored) {
                bgm = null;
            }
        }
        if (bgm != null) {
            try {
                bgm.seekTo(0);
                bgm.start();
            } catch (IllegalStateException ignored) {
                // 播放器状态异常时忽略
            }
        }
    }

    private void notifyTimeChanged() {
        if (timeChangeListener != null) {
            timeChangeListener.onTimeChange(leftTime);
        }
    }

    private void notifyDisrupt(int state) {
        if (toolsChangeListener != null) {
            toolsChangeListener.onDisruptChange(disruptLeftNum, state);
        }
    }

    private void notifyTip(int state) {
        if (toolsChangeListener != null) {
            toolsChangeListener.onTipChange(tipLeftNum, state);
        }
    }

    // ----------------------------------------------------------------------------------
    // 棋盘交互回调
    // ----------------------------------------------------------------------------------

    @Override
    protected void onTileSelected(Point cell) {
        soundPlayer.play(SFX_CHOOSE);
    }

    @Override
    protected void onSelectionCanceled() {
        soundPlayer.play(SFX_CHOOSE);
    }

    @Override
    protected void onLinkCreated(Point a, Point b) {
        soundPlayer.play(SFX_CHOOSE);
    }

    @Override
    protected void onLinkFailed(Point first, Point second) {
        soundPlayer.play(SFX_ERROR);
    }

    @Override
    protected void onMatchRemoved(Point a, Point b) {
        soundPlayer.play(SFX_DISAPPEAR);
        if (!running) {
            return;
        }
        if (isCleared()) {
            finishGame(STATE_WIN);
            return;
        }
        // 没有任何可消除的组合时自动洗牌，避免死局
        if (!hasMove()) {
            shuffleBoard();
            soundPlayer.play(SFX_DISRUPT);
            MatchToast.show(getContext(), R.string.auto_shuffle);
        }
    }

    // ----------------------------------------------------------------------------------
    // 状态查询
    // ----------------------------------------------------------------------------------

    public int getLevel() {
        return level;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public int getLeftTime() {
        return leftTime;
    }

    public int getUsedTime() {
        return Math.max(0, totalTime - leftTime);
    }

    public int getDisruptLeftNum() {
        return disruptLeftNum;
    }

    public int getTipLeftNum() {
        return tipLeftNum;
    }

    public boolean isRunning() {
        return running;
    }

    public void setOnTimeChangeListener(OnTimeChangeListener listener) {
        this.timeChangeListener = listener;
    }

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    public void setOnToolsChangeListener(OnToolsChangeListener listener) {
        this.toolsChangeListener = listener;
    }
}
