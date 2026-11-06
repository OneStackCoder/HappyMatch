package com.happymatch.game;

/**
 * 剩余时间回调。
 */
public interface OnTimeChangeListener {

    /**
     * @param leftTime 剩余秒数
     */
    void onTimeChange(int leftTime);
}
