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
    private static final float GAIN = 2f;

    private WindowManager wm;
    private SharedPreferences sp;
    private CursorView cursor;
    private PadView pad;
    private WindowManager.LayoutParams padLp;
    private WindowManager.LayoutParams cursorLp;
    private float curX, curY;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(this);

        DisplayMetrics m = screen();
        curX = m.widthPixels / 2f;
        curY = m.heightPixels / 2f;

        cursor = new CursorView(this);
        cursorLp = new WindowManager.LayoutParams(
                dp(48), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        cursorLp.gravity = Gravity.TOP | Gravity.START;
        setCursorLp();
        wm.addView(cursor, cursorLp);

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

    // ---------- Posisi kursor dan gestur ke layar ----------

    private DisplayMetrics screen() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return m;
    }

    private void setCursorLp() {
        cursorLp.x = Math.round(curX) - cursorLp.width / 2;
        cursorLp.y = Math.round(curY) - cursorLp.height / 2;
    }

    private void moveCursorBy(float dx, float dy) {
        if (cursor == null) return;
        DisplayMetrics m = screen();
        curX = Math.max(0, Math.min(m.widthPixels - 1, curX + dx));
        curY = Math.max(0, Math.min(m.heightPixels - 1, curY + dy));
        setCursorLp();
        try {
            wm.updateViewLayout(cursor, cursorLp);
        } catch (Exception ignored) {}
    }

    private void send(Path p, long dur) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, dur));
        dispatchGesture(b.build(), null, null);
    }

    private void doTap() {
        Path p = new Path();
        p.moveTo(curX, curY);
        send(p, 50);
    }

    // Tahan lalu geser: tarik pelan dari titik awal ke posisi kursor sekarang
    private void doDrag(float sx, float sy, float ex, float ey) {
        float dist = (float) Math.hypot(ex - sx, ey - sy);
        if (dist < 5) return;
        Path p = new Path();
        p.moveTo(sx, sy);
        p.lineTo(ex, ey);
        send(p, Math.max(300, (long) (dist * 2)));
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
        private float downX, downY, lastX, lastY, dragSX, dragSY;
        private long downT;
        private boolean hold, moved;

        private final Runnable holdRun = new Runnable() {
            public void run() {
                if (!moved) {
                    hold = true;
                    dragSX = curX;
                    dragSY = curY;
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
                    downX = lastX = e.getX();
                    downY = lastY = e.getY();
                    downT = System.currentTimeMillis();
                    hold = false;
                    moved = false;
                    postDelayed(holdRun, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                            moved = true;
                            if (!hold) removeCallbacks(holdRun);
                            lastX = e.getX();
                            lastY = e.getY();
                        }
                    } else {
                        moveCursorBy((e.getX() - lastX) * GAIN, (e.getY() - lastY) * GAIN);
                        lastX = e.getX();
                        lastY = e.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    removeCallbacks(holdRun);
                    long dt = System.currentTimeMillis() - downT;
                    if (!moved) {
                        if (!hold && dt < HOLD_MS) doTap();
                    } else if (hold) {
                        doDrag(dragSX, dragSY, curX, curY);
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
