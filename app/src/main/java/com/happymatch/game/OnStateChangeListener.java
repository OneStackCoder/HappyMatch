package com.happymatch.game;

/**
 * 游戏状态回调：胜利 / 失败。
 */
public interface OnStateChangeListener {

    int STATE_WIN = GameView.STATE_WIN;
    int STATE_LOSE = GameView.STATE_LOSE;

    void onStateChange(int state);
}
