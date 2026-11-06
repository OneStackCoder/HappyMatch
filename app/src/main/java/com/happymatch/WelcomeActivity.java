package com.happymatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * 启动页：展示欢迎图，短暂停留后进入主界面，点击可跳过。
 */
public class WelcomeActivity extends Activity {

    private static final long SPLASH_DELAY = 1300L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped;

    private final Runnable goMainRunnable = new Runnable() {
        @Override
        public void run() {
            goMain();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        View root = (View) findViewById(R.id.welcome_root);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goMain();
            }
        });
        handler.postDelayed(goMainRunnable, SPLASH_DELAY);
    }

    private void goMain() {
        if (jumped) {
            return;
        }
        jumped = true;
        handler.removeCallbacks(goMainRunnable);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(goMainRunnable);
        super.onDestroy();
    }
}
