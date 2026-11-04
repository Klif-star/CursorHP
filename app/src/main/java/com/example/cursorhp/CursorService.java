package com.example.cursorhp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class CursorService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long HOLD_MS = 350;

    private WindowManager wm;
    private SharedPreferences sp;
    private CursorView cursor;
    private PadView pad;
    private WindowManager.LayoutParams padLp;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(this);

        cursor = new CursorView(this);
        WindowManager.LayoutParams cl = new WindowManager.LayoutParams(
                dp(48), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        cl.gravity = Gravity.CENTER;
        wm.addView(cursor, cl);

        pad = new PadView(this);
        padLp = new WindowManager.LayoutParams(
                dp(120), dp(120),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        padLp.gravity = Gravity.BOTTOM | Gravity.END;
        padLp.x = dp(24);
        padLp.y = dp(80);
        wm.addView(pad, padLp);

        applyPrefs();
        updateVisibility();
    }

    private void applyPrefs() {
        if (pad == null) return;
        int size = dp(sp.getInt("size", 120));
        padLp.width = size;
        padLp.height = size;
        pad.circle = sp.getString("shape", "circle").equals("circle");
        pad.setAlpha(sp.getInt("opacity", 70) / 100f);
        wm.updateViewLayout(pad, padLp);
        pad.invalidate();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        applyPrefs();
    }

    private boolean keyboardShown() {
        List<AccessibilityWindowInfo> ws = getWindows();
        if (ws == null) return false;
        for (AccessibilityWindowInfo w : ws) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
        }
        return false;
    }

    private void updateVisibility() {
        if (pad == null || cursor == null) return;
        int vis = keyboardShown() ? View.GONE : View.VISIBLE;
        if (pad.getVisibility() != vis) pad.setVisibility(vis);
        if (cursor.getVisibility() != vis) cursor.setVisibility(vis);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        updateVisibility();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(this);
        try {
            if (cursor != null) wm.removeView(cursor);
            if (pad != null) wm.removeView(pad);
        } catch (Exception ignored) {}
        cursor = null;
        pad = null;
    }

    // ---------- Gestur ke layar ----------

    private float[] center() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        if (cursor != null && cursor.getWidth() > 0) {
            int[] loc = new int[2];
            cursor.getLocationOnScreen(loc);
            return new float[]{loc[0] + cursor.getWidth() / 2f, loc[1] + cursor.getHeight() / 2f};
        }
        return new float[]{m.widthPixels / 2f, m.heightPixels / 2f};
    }

    private void send(Path p, long dur) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, dur));
        dispatchGesture(b.build(), null, null);
    }

    private void doTap() {
        float[] c = center();
        Path p = new Path();
        p.moveTo(c[0], c[1]);
        send(p, 50);
    }

    private void doSwipe(float dx, float dy, long dur) {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float[] c = center();
        float ex = Math.max(5, Math.min(m.widthPixels - 5, c[0] + dx));
        float ey = Math.max(5, Math.min(m.heightPixels - 5, c[1] + dy));
        Path p = new Path();
        p.moveTo(c[0], c[1]);
        p.lineTo(ex, ey);
        send(p, dur);
    }

    // Tahan lalu geser: tarik pelan dari posisi kursor
    private void doDrag(float dx, float dy) {
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 5) return;
        doSwipe(dx * 2, dy * 2, Math.max(300, (long) (dist * 3)));
    }

    // Geser cepat: kibasan cepat (ganti halaman / scroll cepat)
    private void doFling(float dx, float dy) {
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1) return;
        DisplayMetrics m = getResources().getDisplayMetrics();
        float ux = dx / dist, uy = dy / dist;
        float len = 0.6f * (Math.abs(ux) * m.widthPixels + Math.abs(uy) * m.heightPixels);
        doSwipe(ux * len, uy * len, 100);
    }

    // ---------- Tampilan ----------

    private class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            fill.setStyle(Paint.Style.FILL);
            line.setColor(0xFF000000);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(1.5f));
        }

        private int dp(float v) {
            return (int) (v * getResources().getDisplayMetrics().density);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            Path p = new Path();
            p.moveTo(cx, cy);
            p.lineTo(cx, cy + 17 * s);
            p.lineTo(cx + 4.5f * s, cy + 13 * s);
            p.lineTo(cx + 8 * s, cy + 21 * s);
            p.lineTo(cx + 11 * s, cy + 20 * s);
            p.lineTo(cx + 7.5f * s, cy + 12 * s);
            p.lineTo(cx + 13 * s, cy + 12 * s);
            p.close();
            canvas.drawPath(p, fill);
            canvas.drawPath(p, line);
        }
    }

    private class PadView extends View {
        boolean circle = true;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slop;
        private float downX, downY;
        private long downT;
        private boolean hold, moved;

        private final Runnable holdRun = new Runnable() {
            public void run() {
                if (!moved) {
                    hold = true;
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    invalidate();
                }
            }
        };

        PadView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            fill.setStyle(Paint.Style.FILL);
            line.setStyle(Paint.Style.STROKE);
            line.setColor(0xFFFFFFFF);
            line.setStrokeWidth(4);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            fill.setColor(hold ? 0xFF2196F3 : 0xFF607D8B);
            RectF r = new RectF(4, 4, getWidth() - 4, getHeight() - 4);
            if (circle) {
                canvas.drawOval(r, fill);
                canvas.drawOval(r, line);
            } else {
                float rad = getWidth() * 0.12f;
                canvas.drawRoundRect(r, rad, rad, fill);
                canvas.drawRoundRect(r, rad, rad, line);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    downT = System.currentTimeMillis();
                    hold = false;
                    moved = false;
                    postDelayed(holdRun, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                        moved = true;
                        if (!hold) removeCallbacks(holdRun);
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    removeCallbacks(holdRun);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    long dt = System.currentTimeMillis() - downT;
                    if (!moved) {
                        if (!hold && dt < HOLD_MS) doTap();
                    } else if (hold) {
                        doDrag(dx, dy);
                    } else {
                        doFling(dx, dy);
                    }
                    hold = false;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(holdRun);
                    hold = false;
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
