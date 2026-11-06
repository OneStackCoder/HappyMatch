package com.happymatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.happymatch.game.GameView;
import com.happymatch.game.OnStateChangeListener;
import com.happymatch.game.OnTimeChangeListener;
import com.happymatch.game.OnToolsChangeListener;
import com.happymatch.ui.MatchDialog;
import com.happymatch.ui.MatchToast;

/**
 * 游戏主界面：负责界面绑定、开始游戏、道具按钮与结算展示。
 */
public class MainActivity extends Activity implements OnTimeChangeListener,
        OnStateChangeListener, OnToolsChangeListener {

    private LinearLayout startPanel;
    private LinearLayout gamePanel;
    private FrameLayout gameContainer;
    private ImageButton btnStart;
    private ImageButton btnDisrupt;
    private ImageButton btnTip;
    private TextView tvLevel;
    private TextView tvTime;
    private TextView tvDisruptNum;
    private TextView tvTipNum;
    private ProgressBar progressTime;

    private GameView gameView;
    private MediaPlayer initMusic;

    private boolean gameStarted;
    private boolean released;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        bindViews();
        createGameView();
        bindListeners();

        btnStart.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_anim));
        refreshUi();
        startInitMusic();
    }

    private void bindViews() {
        startPanel = (LinearLayout) findViewById(R.id.start_panel);
        gamePanel = (LinearLayout) findViewById(R.id.game_panel);
        gameContainer = (FrameLayout) findViewById(R.id.game_container);
        btnStart = (ImageButton) findViewById(R.id.btn_start);
        btnDisrupt = (ImageButton) findViewById(R.id.btn_disrupt);
        btnTip = (ImageButton) findViewById(R.id.btn_tip);
        tvLevel = (TextView) findViewById(R.id.tv_level);
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvDisruptNum = (TextView) findViewById(R.id.tv_disrupt_num);
        tvTipNum = (TextView) findViewById(R.id.tv_tip_num);
        progressTime = (ProgressBar) findViewById(R.id.progress_time);

        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmExit();
            }
        });
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });
        btnDisrupt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playShake(btnDisrupt);
                gameView.useDisrupt();
            }
        });
        btnTip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playShake(btnTip);
                gameView.useTip();
            }
        });
    }

    private void createGameView() {
        gameView = new GameView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        gameContainer.addView(gameView, params);
    }

    private void bindListeners() {
        gameView.setOnTimeChangeListener(this);
        gameView.setOnStateChangeListener(this);
        gameView.setOnToolsChangeListener(this);
    }

    private void startGame() {
        if (gameStarted) {
            return;
        }
        gameStarted = true;
        releaseInitMusic();

        startPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        gamePanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.trans_in));
        gameView.start();
        refreshUi();
    }

    /** 根据 GameView 当前状态刷新顶栏、底部道具与进度条 */
    private void refreshUi() {
        tvLevel.setText(getString(R.string.level_format, gameView.getLevel()));
        progressTime.setMax(gameView.getTotalTime());
        progressTime.setProgress(gameView.getLeftTime());
        tvTime.setText(getString(R.string.time_format, gameView.getLeftTime()));
        tvDisruptNum.setText(getString(R.string.tool_count_format, gameView.getDisruptLeftNum()));
        tvTipNum.setText(getString(R.string.tool_count_format, gameView.getTipLeftNum()));
    }

    private void playShake(View view) {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        view.startAnimation(shake);
    }

    // ----------------------------------------------------------------------------------
    // GameView 回调
    // ----------------------------------------------------------------------------------

    @Override
    public void onTimeChange(int leftTime) {
        progressTime.setProgress(leftTime);
        tvTime.setText(getString(R.string.time_format, leftTime));
    }

    @Override
    public void onStateChange(int state) {
        boolean win = state == GameView.STATE_WIN;
        String title = getString(win ? R.string.win_title : R.string.lose_title);
        MatchDialog dialog = new MatchDialog(this, title, gameView.getUsedTime(), win,
                new MatchDialog.ActionListener() {
                    @Override
                    public void onNextLevel() {
                        gameView.nextLevel();
                        refreshUi();
                    }

                    @Override
                    public void onReplay() {
                        gameView.replay();
                        refreshUi();
                    }

                    @Override
                    public void onExit() {
                        exitGame();
                    }
                });
        dialog.show();
    }

    @Override
    public void onDisruptChange(int leftNum, int state) {
        tvDisruptNum.setText(getString(R.string.tool_count_format, leftNum));
        if (state == TOOL_USED_UP) {
            MatchToast.show(this, R.string.tool_disrupt_used_up);
        }
    }

    @Override
    public void onTipChange(int leftNum, int state) {
        tvTipNum.setText(getString(R.string.tool_count_format, leftNum));
        if (state == TOOL_USED_UP) {
            MatchToast.show(this, R.string.tool_tip_used_up);
        } else if (state == TOOL_FAIL) {
            MatchToast.show(this, R.string.tool_tip_fail);
        }
    }

    // ----------------------------------------------------------------------------------
    // 生命周期与退出
    // ----------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (gameStarted) {
            gameView.resume();
        } else {
            startInitMusic();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameStarted) {
            gameView.pause();
        } else {
            pauseInitMusic();
        }
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        exitGame();
                    }
                })
                .setCancelable(true)
                .show();
    }

    private void exitGame() {
        if (released) {
            return;
        }
        released = true;
        if (gameView != null) {
            gameView.release();
        }
        releaseInitMusic();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (!released) {
            released = true;
            if (gameView != null) {
                gameView.release();
            }
        }
        releaseInitMusic();
        super.onDestroy();
    }

    // ----------------------------------------------------------------------------------
    // 开始界面背景音乐
    // ----------------------------------------------------------------------------------

    private void startInitMusic() {
        if (initMusic == null) {
            try {
                initMusic = MediaPlayer.create(this, R.raw.init_bg);
                if (initMusic != null) {
                    initMusic.setLooping(true);
                }
            } catch (Exception ignored) {
                initMusic = null;
            }
        }
        if (initMusic != null) {
            try {
                initMusic.start();
            } catch (IllegalStateException ignored) {
                // 播放器状态异常时忽略
            }
        }
    }

    private void pauseInitMusic() {
        if (initMusic != null && initMusic.isPlaying()) {
            initMusic.pause();
        }
    }

    private void releaseInitMusic() {
        if (initMusic != null) {
            try {
                initMusic.release();
            } catch (Exception ignored) {
                // 已释放时忽略
            }
            initMusic = null;
        }
    }
}
