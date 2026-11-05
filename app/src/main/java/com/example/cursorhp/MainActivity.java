package com.example.cursorhp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    static final int SIZE_MIN = 60, SIZE_MAX = 250;
    static final int OP_MIN = 5, OP_MAX = 100;
    private static final int REQ_IMG = 11;

    private static final String[] ACT_LABELS = {
            "Swipe (kibas jauh)", "Scroll (gulir)", "Kembali", "Home",
            "Aplikasi terbaru", "Notifikasi", "Tidak ada"};
    private static final String[] ACT_VALUES = {
            "swipe", "scroll", "back", "home", "recents", "notif", "none"};

    private static final String[] SLOT_KEYS = {"cur_normal", "cur_text", "cur_load"};
    private static final String[] SLOT_DEFS = {"arrow", "ibeam", "spinner"};

    private interface Fmt {
        String text(int v);
    }

    private SharedPreferences sp;
    private LinearLayout root;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private File imgDir() {
        File d = new File(getFilesDir(), "cursors");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private TextView label(String text, int topDp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(topDp), 0, 0);
        root.addView(t);
        return t;
    }

    private void section(String text) {
        TextView t = label(text, 24);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void addSeek(final String key, int min, int max, int def, final Fmt fmt) {
        final int lo = min;
        final TextView lab = label("", 16);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int cur = sp.getInt(key, def);
        bar.setProgress(cur - min);
        lab.setText(fmt.text(cur));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                int v = p + lo;
                lab.setText(fmt.text(v));
                sp.edit().putInt(key, v).apply();
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(bar);
    }

    private void addSwitch(String text, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setPadding(0, dp(12), 0, dp(4));
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean(key, on).apply();
            }
        });
        root.addView(sw);
    }

    private void addFlick(String title, final String key, final String def) {
        label(title, 12);
        Spinner spin = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, ACT_LABELS);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(ad);
        String cur = sp.getString(key, def);
        for (int i = 0; i < ACT_VALUES.length; i++) {
            if (ACT_VALUES[i].equals(cur)) spin.setSelection(i);
        }
        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                String val = ACT_VALUES[pos];
                if (!val.equals(sp.getString(key, def))) {
                    sp.edit().putString(key, val).apply();
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spin);
    }

    // Satu keadaan kursor: pilih bentuk bawaan atau gambar tersimpan, tambah atau hapus gambar
    private void addSlot(String title, final String key, final String def,
                         String hotKey, boolean hotDef, boolean allowSame) {
        section(title);
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<String> values = new ArrayList<String>();
        if (allowSame) {
            labels.add("Sama seperti kursor normal");
            values.add("same");
        }
        String[] bl = {"Panah", "Titik", "Silang", "Teks (I-beam)", "Cincin loading"};
        String[] bv = {"arrow", "dot", "cross", "ibeam", "spinner"};
        for (int i = 0; i < bl.length; i++) {
            labels.add(bl[i]);
            values.add(bv[i]);
        }
        File[] fs = imgDir().listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                if (f.getName().endsWith(".png")) {
                    labels.add("Gambar: " + f.getName().replace(".png", ""));
                    values.add("img:" + f.getName());
                }
            }
        }

        final Spinner spin = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(ad);
        int idx = values.indexOf(sp.getString(key, def));
        if (idx >= 0) spin.setSelection(idx);
        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                String val = values.get(pos);
                if (!val.equals(sp.getString(key, def))) {
                    sp.edit().putString(key, val).apply();
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spin);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button add = new Button(this);
        add.setText("Tambah gambar");
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().putString("pending_slot", key).commit();
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_IMG);
            }
        });
        Button del = new Button(this);
        del.setText("Hapus gambar ini");
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String val = values.get(spin.getSelectedItemPosition());
                if (!val.startsWith("img:")) {
                    Toast.makeText(MainActivity.this,
                            "Pilih gambar tersimpan dulu", Toast.LENGTH_SHORT).show();
                    return;
                }
                new File(imgDir(), val.substring(4)).delete();
                SharedPreferences.Editor ed = sp.edit();
                for (int i = 0; i < SLOT_KEYS.length; i++) {
                    if (val.equals(sp.getString(SLOT_KEYS[i], SLOT_DEFS[i]))) {
                        ed.putString(SLOT_KEYS[i], SLOT_DEFS[i]);
                    }
                }
                ed.apply();
                recreate();
            }
        });
        row.addView(add, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(del, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        addSwitch("Titik klik di tengah gambar (mati = pojok kiri atas)", hotKey, hotDef);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));

        TextView title = new TextView(this);
        title.setText("Cursor HP");
        title.setTextSize(24);
        root.addView(title);

        addSwitch("Kursor aktif", "enabled", true);

        Button open = new Button(this);
        open.setText("Aktifkan (Pengaturan Aksesibilitas)");
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(open);

        section("Kecepatan kursor");
        addSeek("speed", 5, 50, 20, new Fmt() {
            public String text(int v) {
                return "Kecepatan: " + (v / 10f) + "x gerakan jari";
            }
        });

        section("Geser cepat");
        addSwitch("Aktifkan geser cepat", "flick_on", true);
        addSeek("flick_thr", 600, 3000, 1400, new Fmt() {
            public String text(int v) {
                return "Batas kecepatan: " + v + " (makin besar makin harus dikibas keras)";
            }
        });
        addFlick("Kibas ke atas", "flick_up", "scroll");
        addFlick("Kibas ke bawah", "flick_down", "scroll");
        addFlick("Kibas ke kiri", "flick_left", "swipe");
        addFlick("Kibas ke kanan", "flick_right", "swipe");

        section("Tahan + geser");
        addSwitch("Kursor ikut bergerak saat tahan + geser (mati = kursor diam)",
                "live_follow", false);

        section("Efek");
        addSwitch("Jejak kursor (trail)", "trail", false);
        addSwitch("Efek ketuk", "tap_fx", false);

        section("Ukuran dan gambar kursor");
        addSeek("cursor_size", 16, 96, 24, new Fmt() {
            public String text(int v) {
                return "Ukuran kursor: " + v + " (gambar kecil otomatis diperbesar sampai ukuran ini)";
            }
        });
        addSwitch("Gambar sendiri: hapus warna latar", "custom_rm", true);
        addSeek("custom_tol", 5, 120, 40, new Fmt() {
            public String text(int v) {
                return "Kekuatan hapus latar: " + v + " (naikkan kalau latar masih tersisa)";
            }
        });

        addSlot("Kursor normal", "cur_normal", "arrow", "hot_normal", false, false);
        addSlot("Kursor di atas kolom teks", "cur_text", "ibeam", "hot_text", true, true);
        addSlot("Kursor loading (saat membuka aplikasi)", "cur_load", "spinner", "hot_load", true, true);
        addSeek("load_ms", 0, 3000, 1200, new Fmt() {
            public String text(int v) {
                return v == 0 ? "Animasi loading: mati" : "Lama animasi loading: " + v + " ms";
            }
        });

        section("Lingkaran kontrol");
        label("Bentuk", 8);
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rc = new RadioButton(this);
        rc.setText("Lingkaran");
        rc.setId(1);
        RadioButton rs = new RadioButton(this);
        rs.setText("Kotak");
        rs.setId(2);
        rg.addView(rc);
        rg.addView(rs);
        rg.check(sp.getString("shape", "circle").equals("circle") ? 1 : 2);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                sp.edit().putString("shape", id == 1 ? "circle" : "square").apply();
            }
        });
        root.addView(rg);

        addSeek("size", SIZE_MIN, SIZE_MAX, 120, new Fmt() {
            public String text(int v) {
                return "Ukuran: " + v;
            }
        });
        addSeek("opacity", OP_MIN, OP_MAX, 70, new Fmt() {
            public String text(int v) {
                return "Opasitas: " + v + "%";
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMG || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in1 = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in1, null, o);
            if (in1 != null) in1.close();

            int sample = 1;
            while (o.outWidth / sample > 512 || o.outHeight / sample > 512) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, o2);
            if (in2 != null) in2.close();
            if (bmp == null) {
                Toast.makeText(this, "Gambar tidak bisa dibaca", Toast.LENGTH_LONG).show();
                return;
            }

            String name = "img_" + System.currentTimeMillis() + ".png";
            FileOutputStream out = new FileOutputStream(new File(imgDir(), name));
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            String slot = sp.getString("pending_slot", "cur_normal");
            SharedPreferences.Editor ed = sp.edit();
            ed.putString(slot, "img:" + name);
            if (sp.getInt("cursor_size", 24) < 40) ed.putInt("cursor_size", 40);
            ed.apply();
            Toast.makeText(this, "Gambar disimpan dan dipasang", Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan gambar", Toast.LENGTH_LONG).show();
        }
    }
}
