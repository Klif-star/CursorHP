package com.example.cursorhp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    static final int SIZE_MIN = 60, SIZE_MAX = 250;
    static final int OP_MIN = 20, OP_MAX = 100;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Cursor HP");
        title.setTextSize(24);
        root.addView(title);

        Button open = new Button(this);
        open.setText("Aktifkan (Pengaturan Aksesibilitas)");
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(open);

        TextView shapeLabel = new TextView(this);
        shapeLabel.setText("Bentuk");
        shapeLabel.setPadding(0, dp(16), 0, 0);
        root.addView(shapeLabel);

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

        final TextView sizeLabel = new TextView(this);
        sizeLabel.setPadding(0, dp(16), 0, 0);
        root.addView(sizeLabel);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(SIZE_MAX - SIZE_MIN);
        int size = sp.getInt("size", 120);
        sizeBar.setProgress(size - SIZE_MIN);
        sizeLabel.setText("Ukuran: " + size);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                int v = p + SIZE_MIN;
                sizeLabel.setText("Ukuran: " + v);
                sp.edit().putInt("size", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(sizeBar);

        final TextView opLabel = new TextView(this);
        opLabel.setPadding(0, dp(16), 0, 0);
        root.addView(opLabel);
        SeekBar opBar = new SeekBar(this);
        opBar.setMax(OP_MAX - OP_MIN);
        int op = sp.getInt("opacity", 70);
        opBar.setProgress(op - OP_MIN);
        opLabel.setText("Opasitas: " + op + "%");
        opBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                int v = p + OP_MIN;
                opLabel.setText("Opasitas: " + v + "%");
                sp.edit().putInt("opacity", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(opBar);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
}
