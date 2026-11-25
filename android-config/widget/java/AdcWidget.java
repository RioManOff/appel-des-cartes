package com.appeldescartes.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget « L'Appel des Cartes » : valeur et nombre de cartes, choix de la série,
 * booster / display / case, et un bouton qui ouvre l'appli directement sur l'ouverture.
 * Les chiffres viennent de l'appli (plugin AdcWidget) : ils sont rangés dans les préférences « adc_widget ».
 */
public class AdcWidget extends AppWidgetProvider {
    static final String PREFS = "adc_widget";
    static final String A_PREV = "com.appeldescartes.app.W_PREV";
    static final String A_NEXT = "com.appeldescartes.app.W_NEXT";
    static final String A_TYPE = "com.appeldescartes.app.W_TYPE";
    static final String A_TICK = "com.appeldescartes.app.W_TICK";
    static final String A_OPEN = "com.appeldescartes.app.W_OPEN";
    static final String A_HOME = "com.appeldescartes.app.W_HOME";
    static final String X_SERIE = "adc_serie";
    static final String X_TYPE = "adc_type";
    static final String X_KIND = "adc_kind";

    static final String[] KINDS = {"pack", "box", "case"};
    static final String[] KLAB = {"🎴 Booster", "📦 Display", "🗃️ Case"};
    static final String[] KUN = {"un booster", "un display", "une case"};
    static final String[] KTON = {"ton booster", "ton display", "ta case"};
    static final String[] KNEXT = {"Prochain booster", "Prochain display", "Prochaine case"};
    static final String[][] DEF = {
        {"FB01", "Awakened Pulse"}, {"FB02", "Blazing Aura"}, {"FB03", "Raging Roar"}, {"FB04", "Ultra Limit"},
        {"FB05", "New Adventure"}, {"FB06", "Rivals Clash"}, {"FB07", "Wish for Shenron"}, {"FB08", "Saiyan's Pride"},
        {"FB09", "Dual Evolution"}, {"FB10", "Cross Force"}, {"FB11", "Brightness of Hope"}};

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static JSONObject data(Context c) {
        String s = prefs(c).getString("data", null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** séries proposées : [code, nom, jeu] */
    static List<String[]> series(JSONObject d) {
        List<String[]> l = new ArrayList<>();
        JSONArray a = d == null ? null : d.optJSONArray("series");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.optJSONArray(i);
                if (x == null || x.optString(0, "").isEmpty()) continue;
                l.add(new String[]{x.optString(0), x.optString(1, ""), x.optString(2, "fw")});
            }
        }
        if (l.isEmpty()) for (String[] x : DEF) l.add(new String[]{x[0], x[1], "fw"});
        return l;
    }

    static int indexOf(List<String[]> l, String id) {
        if (id == null) return -1;
        for (int i = 0; i < l.size(); i++) if (l.get(i)[0].equals(id)) return i;
        return -1;
    }

    static String curSerie(Context c, int wid, JSONObject d, List<String[]> l) {
        String s = prefs(c).getString("s_" + wid, null);
        if (indexOf(l, s) >= 0) return s;
        String cur = d == null ? null : d.optString("cur", null);
        if (indexOf(l, cur) >= 0) return cur;
        return l.get(0)[0];
    }

    static int kindIndex(String k) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(k)) return i;
        return 0;
    }

    static String curKind(Context c, int wid) {
        return KINDS[kindIndex(prefs(c).getString("t_" + wid, "pack"))];
    }

    /** 0 = disponible, sinon l'heure (ms) où il revient */
    static long until(JSONObject d, String k) {
        JSONObject cd = d == null ? null : d.optJSONObject("cd");
        return cd == null ? 0 : cd.optLong(k, 0);
    }

    static String pend(JSONObject d, String k) {
        JSONObject p = d == null ? null : d.optJSONObject("pend");
        return p == null ? "" : p.optString(k, "");
    }

    static String fmtLong(long ms) {
        long s = Math.max(60, (ms + 999) / 1000), dd = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (dd > 0) return dd + " j " + h + " h";
        if (h > 0) return h + " h " + (m < 10 ? "0" : "") + m;
        return Math.max(1, m) + " min";
    }

    static String fmtShort(long ms) {
        long s = Math.max(60, (ms + 999) / 1000), dd = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (dd > 0) return dd + " j";
        if (h > 0) return h + " h";
        return Math.max(1, m) + " min";
    }

    static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    static final String X_WID = "adc_wid";

    static PendingIntent bc(Context c, int wid, int slot, String action, String kind) {
        Intent i = new Intent(c, AdcWidget.class);
        i.setAction(action);
        i.putExtra(X_WID, wid);
        if (kind != null) i.putExtra(X_KIND, kind);
        i.setData(Uri.parse("adcwidget://" + wid + "/" + slot));
        return PendingIntent.getBroadcast(c, wid * 8 + slot, i, flags());
    }

    static PendingIntent app(Context c, int wid, String serie, String kind) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction((serie == null ? A_HOME : A_OPEN) + "." + wid);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (serie != null) {
            i.putExtra(X_SERIE, serie);
            i.putExtra(X_TYPE, kind);
        }
        return PendingIntent.getActivity(c, wid * 4 + (serie == null ? 1 : 2), i, flags());
    }

    static PendingIntent picker(Context c, int wid) {
        Intent i = new Intent(c, AdcPickActivity.class);
        i.setAction("com.appeldescartes.app.W_PICK." + wid);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(c, wid * 4 + 3, i, flags());
    }

    static final int[] CHIP = {R.id.w_k0, R.id.w_k1, R.id.w_k2};
    static final int[] CHIPA = {R.id.w_k0a, R.id.w_k1a, R.id.w_k2a};
    static final int[] CHIPB = {R.id.w_k0b, R.id.w_k1b, R.id.w_k2b};

    static RemoteViews build(Context c, int wid) {
        JSONObject d = data(c);
        long now = System.currentTimeMillis();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.adc_widget);

        if (d == null) {
            v.setTextViewText(R.id.w_val, "L’Appel des Cartes");
            v.setTextViewText(R.id.w_sub, "Ouvre l\u2019appli une fois");
        } else {
            v.setTextViewText(R.id.w_val, d.optString("v", "—"));
            v.setTextViewText(R.id.w_sub, d.optString("n", ""));
        }

        List<String[]> l = series(d);
        String se = curSerie(c, wid, d, l);
        String[] sx = l.get(Math.max(0, indexOf(l, se)));
        v.setTextViewText(R.id.w_ser_id, sx[0]);
        v.setTextViewText(R.id.w_ser_nm, sx[1] + ("ms".equals(sx[2]) ? " · Masters" : ""));

        String k = curKind(c, wid);
        int ki = kindIndex(k);
        for (int i = 0; i < 3; i++) {
            String kk = KINDS[i];
            long u = until(d, kk);
            String pe = pend(d, kk);
            String st = d == null ? "" : !pe.isEmpty() ? "▶ à reprendre" : u <= now ? "Dispo ✓" : "dans " + fmtShort(u - now);
            boolean on = i == ki;
            v.setTextViewText(CHIPA[i], KLAB[i]);
            v.setTextViewText(CHIPB[i], st);
            v.setInt(CHIP[i], "setBackgroundResource", on ? R.drawable.adc_w_chip_on : R.drawable.adc_w_chip);
            v.setTextColor(CHIPA[i], on ? 0xFFFFFFFF : 0xFFD9D0D0);
            v.setTextColor(CHIPB[i], !pe.isEmpty() || u <= now ? (on ? 0xFFFFD36B : 0xFF7FD08A) : 0xFFA89C9C);
            v.setOnClickPendingIntent(CHIP[i], bc(c, wid, i, A_TYPE, kk));
        }

        long u = until(d, k);
        String pe = pend(d, k);
        String lab;
        boolean go = true;
        if (d == null) lab = "Ouvrir l’appli";
        else if (!pe.isEmpty()) lab = "▶ Reprendre " + KTON[ki] + " " + pe;
        else if (u <= now) lab = "Ouvrir " + KUN[ki] + " " + sx[0];
        else {
            lab = "⏳ " + KNEXT[ki] + " dans " + fmtLong(u - now);
            go = false;
        }
        v.setTextViewText(R.id.w_open, lab);
        v.setInt(R.id.w_open, "setBackgroundResource", go ? R.drawable.adc_w_btn : R.drawable.adc_w_btn_off);
        v.setTextColor(R.id.w_open, go ? 0xFFFFFFFF : 0xFFCFC4C4);

        v.setOnClickPendingIntent(R.id.w_open, app(c, wid, sx[0], k));
        v.setOnClickPendingIntent(R.id.w_head, app(c, wid, null, null));
        v.setOnClickPendingIntent(R.id.w_prev, bc(c, wid, 4, A_PREV, null));
        v.setOnClickPendingIntent(R.id.w_next, bc(c, wid, 5, A_NEXT, null));
        v.setOnClickPendingIntent(R.id.w_ser, picker(c, wid));
        return v;
    }

    static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, AdcWidget.class));
    }

    static int count(Context c) {
        try {
            return ids(c).length;
        } catch (Exception e) {
            return 0;
        }
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (int id : ids(c)) m.updateAppWidget(id, build(c, id));
        } catch (Exception ignored) {
        }
        schedule(c);
    }

    /** se remet à jour tout seul quand un booster / display / case redevient disponible */
    static void schedule(Context c) {
        try {
            JSONObject d = data(c);
            long now = System.currentTimeMillis(), next = 0;
            for (String k : KINDS) {
                long u = until(d, k);
                if (u > now && (next == 0 || u < next)) next = u;
            }
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, AdcWidget.class);
            i.setAction(A_TICK);
            i.setData(Uri.parse("adcwidget://tick"));
            PendingIntent pi = PendingIntent.getBroadcast(c, -1, i, flags());
            if (am == null) return;
            am.cancel(pi);
            if (next > 0 && count(c) > 0) am.set(AlarmManager.RTC, next + 5000, pi);
        } catch (Exception ignored) {
        }
    }

    static void step(Context c, int wid, int dir) {
        JSONObject d = data(c);
        List<String[]> l = series(d);
        int i = Math.max(0, indexOf(l, curSerie(c, wid, d, l)));
        i = ((i + dir) % l.size() + l.size()) % l.size();
        prefs(c).edit().putString("s_" + wid, l.get(i)[0]).apply();
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] wids) {
        for (int id : wids) m.updateAppWidget(id, build(c, id));
        schedule(c);
    }

    @Override
    public void onReceive(Context c, Intent it) {
        super.onReceive(c, it);
        String a = it.getAction();
        if (a == null) return;
        int wid = it.getIntExtra(X_WID, -1);
        if (A_PREV.equals(a) || A_NEXT.equals(a)) {
            if (wid < 0) return;
            step(c, wid, A_NEXT.equals(a) ? 1 : -1);
        } else if (A_TYPE.equals(a)) {
            String k = it.getStringExtra(X_KIND);
            if (wid < 0 || k == null) return;
            prefs(c).edit().putString("t_" + wid, KINDS[kindIndex(k)]).apply();
        } else if (!A_TICK.equals(a)) {
            return;
        }
        updateAll(c);
    }

    @Override
    public void onDeleted(Context c, int[] wids) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int id : wids) {
            e.remove("s_" + id);
            e.remove("t_" + id);
        }
        e.apply();
    }
}
