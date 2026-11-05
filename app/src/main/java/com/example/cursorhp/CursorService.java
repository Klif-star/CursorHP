package com.example.cursorhp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CursorService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long HOLD_MS = 350;

    private WindowManager wm;
    private SharedPreferences sp;
    private CursorView cursor;
    private PadView pad;
    private View menu;
    private EffectView effect;
    private WindowManager.LayoutParams padLp;
    private WindowManager.LayoutParams cursorLp;

    // Posisi ujung kursor di layar (piksel)
    private float curX, curY;

    // Pengaturan
    private float gain = 2f;
    private boolean enabled = true;
    private boolean flickOn = true;
    private float flickThrDp = 1400f;
    private final String[] flickAct = {"scroll", "scroll", "swipe", "swipe"}; // atas, bawah, kiri, kanan
    private String cursorShape = "arrow";
    private int cursorSizeDp = 24;
    private boolean customCenter = false;
    private Bitmap customBmp;
    private boolean customRm = true;
    private int customTol = 40;
    private String customKey = "";
    private boolean trailOn = false;
    private boolean tapFx = false;
    private boolean moveMode = false;
    private boolean liveFollow = false;
    private float liveX, liveY;

    // Mode tahan + geser langsung
    private boolean liveMode = false;
    private boolean liveActive, liveEnding, liveInFlight;
    private float liveSX, liveSY;
    private GestureDescription.StrokeDescription liveStroke;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private DisplayMetrics screen() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return m;
    }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(this);

        DisplayMetrics m = screen();
        curX = m.widthPixels / 2f;
        curY = m.heightPixels / 2f;

        effect = new EffectView(this);
        WindowManager.LayoutParams el = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        el.gravity = Gravity.TOP | Gravity.START;
        wm.addView(effect, el);

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
    }

    private void applyPrefs() {
        if (pad == null || cursor == null) return;

        int size = dp(sp.getInt("size", 120));
        padLp.width = size;
        padLp.height = size;
        padLp.x = sp.getInt("pad_x", dp(24));
        padLp.y = sp.getInt("pad_y", dp(80));
        pad.circle = sp.getString("shape", "circle").equals("circle");
        pad.setAlpha(sp.getInt("opacity", 70) / 100f);
        wm.updateViewLayout(pad, padLp);
        pad.invalidate();

        gain = sp.getInt("speed", 20) / 10f;
        enabled = sp.getBoolean("enabled", true);
        flickOn = sp.getBoolean("flick_on", true);
        flickThrDp = sp.getInt("flick_thr", 1400);
        flickAct[0] = sp.getString("flick_up", "scroll");
        flickAct[1] = sp.getString("flick_down", "scroll");
        flickAct[2] = sp.getString("flick_left", "swipe");
        flickAct[3] = sp.getString("flick_right", "swipe");

        cursorShape = sp.getString("cursor_shape", "arrow");
        cursorSizeDp = sp.getInt("cursor_size", 24);
        customCenter = sp.getBoolean("custom_center", false);
        customRm = sp.getBoolean("custom_rm", true);
        customTol = sp.getInt("custom_tol", 40);
        trailOn = sp.getBoolean("trail", false);
        tapFx = sp.getBoolean("tap_fx", false);
        liveFollow = sp.getBoolean("live_follow", false);
        loadCustom();
        int w = dp(cursorSizeDp) * 2;
        cursorLp.width = w;
        cursorLp.height = w;
        setCursorLp();
        wm.updateViewLayout(cursor, cursorLp);
        cursor.invalidate();

        updateVisibility();
    }

    private void loadCustom() {
        File f = new File(getFilesDir(), "cursor.png");
        if (!f.exists()) {
            customBmp = null;
            customKey = "";
            return;
        }
        String key = f.lastModified() + ":" + customRm + ":" + customTol;
        if (customBmp == null || !key.equals(customKey)) {
            Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (raw != null && customRm) raw = removeBg(raw, customTol);
            customBmp = raw;
            customKey = key;
        }
    }

    private boolean bgMatch(int c, int r, int g, int b, int tol) {
        if (Color.alpha(c) < 30) return true;
        return Math.abs(Color.red(c) - r) <= tol
                && Math.abs(Color.green(c) - g) <= tol
                && Math.abs(Color.blue(c) - b) <= tol;
    }

    // Hapus warna latar yang tersambung ke tepi gambar (warna acuan: pojok kiri atas)
    private Bitmap removeBg(Bitmap src, int tol) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int bg = px[0];
        if (Color.alpha(bg) < 200) return src;
        int r = Color.red(bg), g = Color.green(bg), b = Color.blue(bg);
        boolean[] seen = new boolean[w * h];
        int[] q = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            int[] ids = {x, (h - 1) * w + x};
            for (int i : ids) {
                if (!seen[i] && bgMatch(px[i], r, g, b, tol)) {
                    seen[i] = true;
                    q[tail++] = i;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            int[] ids = {y * w, y * w + w - 1};
            for (int i : ids) {
                if (!seen[i] && bgMatch(px[i], r, g, b, tol)) {
                    seen[i] = true;
                    q[tail++] = i;
                }
            }
        }
        while (head < tail) {
            int i = q[head++];
            int x = i % w, y = i / w;
            int[] ns = {
                    x > 0 ? i - 1 : -1,
                    x < w - 1 ? i + 1 : -1,
                    y > 0 ? i - w : -1,
                    y < h - 1 ? i + w : -1};
            for (int n : ns) {
                if (n >= 0 && !seen[n] && bgMatch(px[n], r, g, b, tol)) {
                    seen[n] = true;
                    q[tail++] = n;
                }
            }
        }
        for (int i = 0; i < tail; i++) px[q[i]] = 0;
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
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
        boolean show = enabled && !keyboardShown();
        int vis = show ? View.VISIBLE : View.GONE;
        if (pad.getVisibility() != vis) pad.setVisibility(vis);
        if (cursor.getVisibility() != vis) cursor.setVisibility(vis);
        if (effect != null && effect.getVisibility() != vis) effect.setVisibility(vis);
        if (!show) {
            hideMenu();
            if (liveActive) endLive();
        }
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
        hideMenu();
        try {
            if (effect != null) wm.removeView(effect);
            if (cursor != null) wm.removeView(cursor);
            if (pad != null) wm.removeView(pad);
        } catch (Exception ignored) {}
        effect = null;
        cursor = null;
        pad = null;
    }

    // ---------- Posisi kursor ----------

    private void setCursorLp() {
        cursorLp.x = Math.round(curX) - cursorLp.width / 2;
        cursorLp.y = Math.round(curY) - cursorLp.height / 2;
    }

    private void placeCursor(float x, float y) {
        if (cursor == null) return;
        DisplayMetrics m = screen();
        float ox = curX, oy = curY;
        curX = Math.max(0, Math.min(m.widthPixels - 1, x));
        curY = Math.max(0, Math.min(m.heightPixels - 1, y));
        if (trailOn && effect != null && (curX != ox || curY != oy)) effect.addTrail(curX, curY);
        setCursorLp();
        try {
            wm.updateViewLayout(cursor, cursorLp);
        } catch (Exception ignored) {}
    }

    private void moveCursorBy(float dx, float dy) {
        placeCursor(curX + dx, curY + dy);
    }

    // ---------- Gestur ke layar ----------

    private void send(Path p, long dur) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, dur));
        dispatchGesture(b.build(), null, null);
    }

    private boolean clickAt(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (x < loc[0] || x > loc[0] + v.getWidth() || y < loc[1] || y > loc[1] + v.getHeight()) {
            return false;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (clickAt(g.getChildAt(i), x, y)) return true;
            }
            return true;
        }
        if (v.isClickable()) v.performClick();
        return true;
    }

    // Gestur dari layanan tidak mengenai jendela milik sendiri, jadi menu dan benjolan ditangani di sini
    private boolean handleOverlayTap() {
        if (menu != null && clickAt(menu, curX, curY)) return true;
        if (pad != null && pad.getVisibility() == View.VISIBLE && pad.getWidth() > 0) {
            int[] loc = new int[2];
            pad.getLocationOnScreen(loc);
            float bx = loc[0] + pad.bumpX(), by = loc[1] + pad.bumpY();
            if (Math.hypot(curX - bx, curY - by) <= pad.bumpR() * 1.6f) {
                toggleMenu();
                return true;
            }
        }
        return false;
    }

    private void doTap() {
        if (handleOverlayTap()) return;
        if (tapFx && effect != null) effect.addRipple(curX, curY);
        Path p = new Path();
        p.moveTo(curX, curY);
        send(p, 50);
    }

    private void doSwipe(float dx, float dy, long dur) {
        DisplayMetrics m = screen();
        float ex = Math.max(5, Math.min(m.widthPixels - 5, curX + dx));
        float ey = Math.max(5, Math.min(m.heightPixels - 5, curY + dy));
        Path p = new Path();
        p.moveTo(curX, curY);
        p.lineTo(ex, ey);
        send(p, dur);
    }

    // Tahan lalu geser lalu lepas: tarik dari titik awal ke posisi kursor sekarang
    private void doDrag(float sx, float sy, float ex, float ey) {
        float dist = (float) Math.hypot(ex - sx, ey - sy);
        if (dist < 5) return;
        Path p = new Path();
        p.moveTo(sx, sy);
        p.lineTo(ex, ey);
        send(p, Math.max(300, (long) (dist * 2)));
    }

    // idx: 0 atas, 1 bawah, 2 kiri, 3 kanan
    private void runFlick(int idx, float ux, float uy) {
        DisplayMetrics m = screen();
        float span = Math.abs(ux) * m.widthPixels + Math.abs(uy) * m.heightPixels;
        String a = flickAct[idx];
        if ("swipe".equals(a)) {
            doSwipe(ux * 0.6f * span, uy * 0.6f * span, 100);
        } else if ("scroll".equals(a)) {
            doSwipe(ux * 0.35f * span, uy * 0.35f * span, 350);
        } else if ("back".equals(a)) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } else if ("home".equals(a)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        } else if ("recents".equals(a)) {
            performGlobalAction(GLOBAL_ACTION_RECENTS);
        } else if ("notif".equals(a)) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
    }

    // ---------- Tahan + geser langsung ----------

    private void startLive() {
        liveActive = true;
        liveEnding = false;
        liveInFlight = false;
        liveStroke = null;
        liveX = curX;
        liveY = curY;
        liveSX = curX;
        liveSY = curY;
        pumpLive();
    }

    private void pumpLive() {
        if (!liveActive || liveInFlight) return;
        final float tx = liveX, ty = liveY;
        boolean first = liveStroke == null;
        if (!first && !liveEnding && Math.hypot(tx - liveSX, ty - liveSY) < 2) return;
        Path p = new Path();
        p.moveTo(liveSX, liveSY);
        if (tx != liveSX || ty != liveSY) p.lineTo(tx, ty);
        final boolean cont = !liveEnding;
        final GestureDescription.StrokeDescription st = first
                ? new GestureDescription.StrokeDescription(p, 0, 20, cont)
                : liveStroke.continueStroke(p, 0, 30, cont);
        liveInFlight = true;
        boolean ok = dispatchGesture(new GestureDescription.Builder().addStroke(st).build(),
                new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription g) {
                        liveInFlight = false;
                        liveStroke = st;
                        liveSX = tx;
                        liveSY = ty;
                        if (!cont) {
                            liveActive = false;
                            liveStroke = null;
                        } else {
                            pumpLive();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription g) {
                        liveInFlight = false;
                        liveActive = false;
                        liveStroke = null;
                    }
                }, null);
        if (!ok) {
            liveInFlight = false;
            liveActive = false;
            liveStroke = null;
        }
    }

    private void moveLive(float dx, float dy) {
        DisplayMetrics m = screen();
        liveX = Math.max(0, Math.min(m.widthPixels - 1, liveX + dx));
        liveY = Math.max(0, Math.min(m.heightPixels - 1, liveY + dy));
        if (liveFollow) placeCursor(liveX, liveY);
    }

    private void endLive() {
        if (!liveActive) return;
        liveEnding = true;
        pumpLive();
    }

    // ---------- Menu benjolan ----------

    private void toggleMenu() {
        if (menu != null) hideMenu();
        else showMenu();
    }

    private void hideMenu() {
        if (menu == null || wm == null) return;
        try {
            wm.removeView(menu);
        } catch (Exception ignored) {}
        menu = null;
    }

    private TextView menuItem(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setMinHeight(dp(44));
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    private LinearLayout speedRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView val = menuItem("", null);
        val.setGravity(Gravity.CENTER);
        val.setText("Kecepatan " + (Math.round(gain * 10) / 10f) + "x");
        TextView minus = menuItem("\u2212", new View.OnClickListener() {
            public void onClick(View v) {
                changeSpeed(-2, val);
            }
        });
        minus.setGravity(Gravity.CENTER);
        minus.setTextSize(22);
        TextView plus = menuItem("+", new View.OnClickListener() {
            public void onClick(View v) {
                changeSpeed(2, val);
            }
        });
        plus.setGravity(Gravity.CENTER);
        plus.setTextSize(22);
        row.addView(minus, new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(val, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void changeSpeed(int delta, TextView label) {
        int v = Math.max(5, Math.min(50, Math.round(gain * 10) + delta));
        gain = v / 10f;
        sp.edit().putInt("speed", v).apply();
        label.setText("Kecepatan " + (v / 10f) + "x");
    }

    private void showMenu() {
        if (menu != null || padLp == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2263238);
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);

        box.addView(menuItem(liveMode ? "Tahan + geser: AKTIF" : "Tahan + geser: MATI",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        liveMode = !liveMode;
                        if (liveMode) moveMode = false;
                        hideMenu();
                        if (pad != null) pad.invalidate();
                    }
                }));
        box.addView(menuItem(moveMode ? "Geser tombol: AKTIF" : "Geser tombol: MATI",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        moveMode = !moveMode;
                        if (moveMode) liveMode = false;
                        hideMenu();
                        if (pad != null) pad.invalidate();
                    }
                }));
        box.addView(speedRow());
        box.addView(menuItem("Kembali", new View.OnClickListener() {
            public void onClick(View v) {
                hideMenu();
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }));
        box.addView(menuItem("Home", new View.OnClickListener() {
            public void onClick(View v) {
                hideMenu();
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }));
        box.addView(menuItem("Aplikasi terbaru", new View.OnClickListener() {
            public void onClick(View v) {
                hideMenu();
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            }
        }));
        box.addView(menuItem("Notifikasi", new View.OnClickListener() {
            public void onClick(View v) {
                hideMenu();
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            }
        }));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = padLp.x;
        lp.y = Math.max(0, Math.min(padLp.y + padLp.height + dp(8),
                screen().heightPixels - dp(380)));
        wm.addView(box, lp);
        menu = box;
    }

    // ---------- Tampilan ----------

    private static class Pt {
        final float x, y;
        final long t;

        Pt(float x, float y, long t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }

    private class EffectView extends View {
        private final ArrayList<Pt> trail = new ArrayList<Pt>();
        private final ArrayList<Pt> ripples = new ArrayList<Pt>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EffectView(Context c) {
            super(c);
        }

        void addTrail(float x, float y) {
            trail.add(new Pt(x, y, SystemClock.uptimeMillis()));
            if (trail.size() > 80) trail.remove(0);
            postInvalidateOnAnimation();
        }

        void addRipple(float x, float y) {
            ripples.add(new Pt(x, y, SystemClock.uptimeMillis()));
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            paint.setStyle(Paint.Style.FILL);
            for (int i = trail.size() - 1; i >= 0; i--) {
                Pt p = trail.get(i);
                float age = (now - p.t) / 450f;
                if (age >= 1f) {
                    trail.remove(i);
                    continue;
                }
                paint.setColor(((int) (170 * (1f - age)) << 24) | 0x2196F3);
                canvas.drawCircle(p.x, p.y, dp(6) * (1f - age * 0.5f), paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            for (int i = ripples.size() - 1; i >= 0; i--) {
                Pt p = ripples.get(i);
                float age = (now - p.t) / 400f;
                if (age >= 1f) {
                    ripples.remove(i);
                    continue;
                }
                paint.setColor(((int) (210 * (1f - age)) << 24) | 0x2196F3);
                canvas.drawCircle(p.x, p.y, dp(8) + dp(32) * age, paint);
            }
            if (!trail.isEmpty() || !ripples.isEmpty()) postInvalidateOnAnimation();
        }
    }

    private class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        CursorView(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            fill.setStyle(Paint.Style.FILL);
            line.setColor(0xFF000000);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float size = dp(cursorSizeDp);
            line.setStrokeWidth(Math.max(1f, size / 16f));

            if ("dot".equals(cursorShape)) {
                canvas.drawCircle(cx, cy, size / 3f, fill);
                canvas.drawCircle(cx, cy, size / 3f, line);
            } else if ("cross".equals(cursorShape)) {
                Paint thick = new Paint(Paint.ANTI_ALIAS_FLAG);
                thick.setStyle(Paint.Style.STROKE);
                thick.setColor(0xFF000000);
                thick.setStrokeWidth(Math.max(3f, size / 6f));
                canvas.drawLine(cx - size / 2f, cy, cx + size / 2f, cy, thick);
                canvas.drawLine(cx, cy - size / 2f, cx, cy + size / 2f, thick);
                thick.setColor(0xFFFFFFFF);
                thick.setStrokeWidth(Math.max(1.5f, size / 12f));
                canvas.drawLine(cx - size / 2f, cy, cx + size / 2f, cy, thick);
                canvas.drawLine(cx, cy - size / 2f, cx, cy + size / 2f, thick);
            } else if ("custom".equals(cursorShape) && customBmp != null) {
                float bw = customBmp.getWidth(), bh = customBmp.getHeight();
                float sc = size / Math.max(bw, bh);
                float w = bw * sc, h = bh * sc;
                RectF dst = customCenter
                        ? new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                        : new RectF(cx, cy, cx + w, cy + h);
                canvas.drawBitmap(customBmp, null, dst, bmpPaint);
            } else {
                float s = size / 24f;
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
    }

    private class PadView extends View {
        boolean circle = true;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bumpFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bumpLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slop;
        private float downX, downY, lastX, lastY, dragSX, dragSY, startCurX, startCurY, mvX, mvY;
        private long downT;
        private boolean hold, moved, live, onBump;
        private VelocityTracker vt;

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
            bumpFill.setStyle(Paint.Style.FILL);
            bumpLine.setStyle(Paint.Style.STROKE);
            bumpLine.setColor(0xFF37474F);
            bumpLine.setStrokeWidth(3);
        }

        private float bumpR() {
            return dp(12);
        }

        private float bumpX() {
            return getWidth() - bumpR() - 2;
        }

        private float bumpY() {
            return bumpR() + 2;
        }

        private boolean nearBump(float x, float y, float radius) {
            return Math.hypot(x - bumpX(), y - bumpY()) <= radius;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean busy = hold || live;
            fill.setColor(busy ? 0xFF2196F3 : (moveMode ? 0xFFEF6C00 : (liveMode ? 0xFF2E7D32 : 0xFF607D8B)));
            RectF r = new RectF(4, 4, getWidth() - 4, getHeight() - 4);
            if (circle) {
                canvas.drawOval(r, fill);
                canvas.drawOval(r, line);
            } else {
                float rad = getWidth() * 0.12f;
                canvas.drawRoundRect(r, rad, rad, fill);
                canvas.drawRoundRect(r, rad, rad, line);
            }
            bumpFill.setColor(moveMode ? 0xFFFFB74D : (liveMode ? 0xFF66BB6A : 0xFFFFFFFF));
            canvas.drawCircle(bumpX(), bumpY(), bumpR(), bumpFill);
            canvas.drawCircle(bumpX(), bumpY(), bumpR(), bumpLine);
        }

        private void recycleVt() {
            if (vt != null) {
                vt.recycle();
                vt = null;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = e.getX();
                    downY = lastY = e.getY();
                    downT = System.currentTimeMillis();
                    onBump = nearBump(downX, downY, dp(20));
                    if (onBump) return true;
                    if (moveMode) {
                        mvX = e.getRawX();
                        mvY = e.getRawY();
                        return true;
                    }
                    startCurX = curX;
                    startCurY = curY;
                    hold = false;
                    moved = false;
                    live = false;
                    recycleVt();
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    if (!liveMode) postDelayed(holdRun, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (onBump) return true;
                    if (moveMode) {
                        DisplayMetrics sm = screen();
                        padLp.x = Math.max(0, Math.min(sm.widthPixels - padLp.width,
                                padLp.x - Math.round(e.getRawX() - mvX)));
                        padLp.y = Math.max(0, Math.min(sm.heightPixels - padLp.height,
                                padLp.y - Math.round(e.getRawY() - mvY)));
                        mvX = e.getRawX();
                        mvY = e.getRawY();
                        try {
                            wm.updateViewLayout(pad, padLp);
                        } catch (Exception ignored) {}
                        return true;
                    }
                    if (vt != null) vt.addMovement(e);
                    if (!moved) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                            moved = true;
                            if (!hold) removeCallbacks(holdRun);
                            lastX = e.getX();
                            lastY = e.getY();
                            if (liveMode) {
                                live = true;
                                startLive();
                                invalidate();
                            }
                        }
                    } else {
                        float mdx = (e.getX() - lastX) * gain, mdy = (e.getY() - lastY) * gain;
                        if (live) {
                            moveLive(mdx, mdy);
                            pumpLive();
                        } else {
                            moveCursorBy(mdx, mdy);
                        }
                        lastX = e.getX();
                        lastY = e.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    removeCallbacks(holdRun);
                    if (onBump) {
                        onBump = false;
                        if (nearBump(e.getX(), e.getY(), dp(24))) toggleMenu();
                        return true;
                    }
                    if (moveMode) {
                        sp.edit().putInt("pad_x", padLp.x).putInt("pad_y", padLp.y).apply();
                        return true;
                    }
                    long dt = System.currentTimeMillis() - downT;
                    if (live) {
                        endLive();
                    } else if (!moved) {
                        if (!hold && dt < HOLD_MS) doTap();
                    } else if (hold) {
                        doDrag(dragSX, dragSY, curX, curY);
                    } else if (flickOn && vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                        float dens = getResources().getDisplayMetrics().density;
                        if (Math.hypot(vx, vy) > flickThrDp * dens) {
                            placeCursor(startCurX, startCurY);
                            if (Math.abs(vx) > Math.abs(vy)) {
                                if (vx > 0) runFlick(3, 1, 0);
                                else runFlick(2, -1, 0);
                            } else {
                                if (vy > 0) runFlick(1, 0, 1);
                                else runFlick(0, 0, -1);
                            }
                        }
                    }
                    recycleVt();
                    hold = false;
                    live = false;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(holdRun);
                    if (live) endLive();
                    recycleVt();
                    onBump = false;
                    hold = false;
                    live = false;
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
