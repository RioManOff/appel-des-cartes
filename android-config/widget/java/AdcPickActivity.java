package com.appeldescartes.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.List;

/** Petite fenêtre ouverte depuis le widget pour choisir la série. */
public class AdcPickActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final int wid = getIntent().getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (wid < 0) {
            finish();
            return;
        }
        final JSONObject d = AdcWidget.data(this);
        final List<String[]> l = AdcWidget.series(d);
        final String cur = AdcWidget.curSerie(this, wid, d, l);
        final String[] items = new String[l.size()];
        int sel = 0;
        for (int i = 0; i < l.size(); i++) {
            String[] s = l.get(i);
            items[i] = s[0] + " · " + s[1] + ("ms".equals(s[2]) ? "  (Masters)" : "");
            if (s[0].equals(cur)) sel = i;
        }
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Choisis la série")
                .setSingleChoiceItems(items, sel, (di, which) -> {
                    AdcWidget.prefs(AdcPickActivity.this).edit().putString("s_" + wid, l.get(which)[0]).apply();
                    AdcWidget.updateAll(AdcPickActivity.this);
                    di.dismiss();
                })
                .setNegativeButton("Annuler", (di, which) -> di.dismiss())
                .create();
        dlg.setOnDismissListener(x -> finish());
        dlg.show();
    }
}
