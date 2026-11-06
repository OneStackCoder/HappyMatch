package com.happymatch.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import com.happymatch.R;

/**
 * 结算对话框：胜利时展示“下一关”，失败时只展示重玩与退出。
 */
public class MatchDialog extends Dialog {

    public interface ActionListener {
        void onNextLevel();

        void onReplay();

        void onExit();
    }

    private final String title;
    private final int usedSeconds;
    private final boolean canNext;
    private final ActionListener listener;

    public MatchDialog(Context context, String title, int usedSeconds,
                       boolean canNext, ActionListener listener) {
        super(context, R.style.DialogTheme);
        this.title = title;
        this.usedSeconds = usedSeconds;
        this.canNext = canNext;
        this.listener = listener;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_match);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        bindViews();
    }

    private void bindViews() {
        TextView tvTitle = (TextView) findViewById(R.id.tv_dialog_title);
        TextView tvTime = (TextView) findViewById(R.id.tv_dialog_time);
        ImageButton btnNext = (ImageButton) findViewById(R.id.btn_dialog_next);
        ImageButton btnReplay = (ImageButton) findViewById(R.id.btn_dialog_replay);
        ImageButton btnExit = (ImageButton) findViewById(R.id.btn_dialog_exit);

        tvTitle.setText(title);
        tvTime.setText(getContext().getString(R.string.used_time_format, usedSeconds));
        btnNext.setVisibility(canNext ? View.VISIBLE : View.GONE);

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) {
                    listener.onNextLevel();
                }
            }
        });
        btnReplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) {
                    listener.onReplay();
                }
            }
        });
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) {
                    listener.onExit();
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
