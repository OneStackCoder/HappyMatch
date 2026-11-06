package com.happymatch.game;

/**
 * 道具使用回调。
 */
public interface OnToolsChangeListener {

    int TOOL_SUCCESS = 0;
    int TOOL_FAIL = 1;
    int TOOL_USED_UP = 2;

    /**
     * 洗牌道具状态变化
     *
     * @param leftNum 剩余次数
     * @param state   {@link #TOOL_SUCCESS}、{@link #TOOL_FAIL}、{@link #TOOL_USED_UP}
     */
    void onDisruptChange(int leftNum, int state);

    /**
     * 提示道具状态变化
     *
     * @param leftNum 剩余次数
     * @param state   {@link #TOOL_SUCCESS}、{@link #TOOL_FAIL}、{@link #TOOL_USED_UP}
     */
    void onTipChange(int leftNum, int state);
}
