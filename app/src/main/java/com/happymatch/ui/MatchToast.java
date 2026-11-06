package com.happymatch.ui;

import android.content.Context;
import android.widget.Toast;

/**
 * 轻量 Toast 封装，避免连续点击时提示排队。
 */
public final class MatchToast {

    private static Toast toast;

    private MatchToast() {
    }

    public static void show(Context context, int resId) {
        if (context == null) {
            return;
        }
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(context.getApplicationContext(), resId, Toast.LENGTH_SHORT);
        toast.show();
    }
}
