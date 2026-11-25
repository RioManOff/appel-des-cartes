package com.appeldescartes.app;

import android.accessibilityservice.AccessibilityService;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.getcapacitor.JSObject;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AdcWidgetTest {

    static Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    static void save(Bitmap b, String name) throws Exception {
        File f = new File(ctx().getFilesDir(), name + ".png");
        FileOutputStream o = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        o.close();
    }

    static void note(String name, String text) throws Exception {
        FileOutputStream o = new FileOutputStream(new File(ctx().getFilesDir(), name + ".txt"));
        o.write(text.getBytes("UTF-8"));
        o.close();
    }

    /** dessine le widget comme le ferait l'écran d'accueil */
    static void shot(final String name, final int wid, final int wdp, final int hdp) throws Exception {
        final Context c = ctx();
        final AtomicReference<Exception> err = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                RemoteViews rv = AdcWidget.build(c, wid);
                FrameLayout parent = new FrameLayout(c);
                View v = rv.apply(c, parent);
                float dn = c.getResources().getDisplayMetrics().density;
                int w = Math.round(wdp * dn), h = Math.round(hdp * dn), pad = Math.round(16 * dn);
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                v.layout(0, 0, w, h);
                Bitmap b = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                cv.drawColor(0xFF35577A);
                cv.translate(pad, pad);
                v.draw(cv);
                save(b, name);
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
    }

    static String data(String pend) {
        long now = System.currentTimeMillis();
        return "{\"v\":\"≈ 1 257 €\",\"n\":\"592 cartes · 2 scellés\",\"cur\":\"FB05\","
                + "\"series\":[[\"FB01\",\"Awakened Pulse\",\"fw\"],[\"FB05\",\"New Adventure\",\"fw\"],[\"FB11\",\"Brightness of Hope\",\"fw\"],[\"BT01\",\"Galactic Battle\",\"ms\"]],"
                + "\"cd\":{\"pack\":0,\"box\":" + (now + 3 * 86400000L + 5 * 3600000L) + ",\"case\":" + (now + 5 * 3600000L + 20 * 60000L) + "},"
                + "\"pend\":{\"pack\":\"\",\"box\":\"\",\"case\":\"" + pend + "\"}}";
    }

    @Test
    public void a_dessin_et_boutons() throws Exception {
        Context c = ctx();
        AdcWidget.prefs(c).edit().clear().commit();
        shot("w0_sans_donnees", 7, 320, 196);

        AdcWidget.prefs(c).edit().putString("data", data("")).commit();
        shot("w1_booster_dispo", 7, 320, 196);
        assertEquals("FB05", AdcWidget.curSerie(c, 7, AdcWidget.data(c), AdcWidget.series(AdcWidget.data(c))));

        // flèche ›  : FB05 -> FB11
        new AdcWidget().onReceive(c, new Intent(c, AdcWidget.class).setAction(AdcWidget.A_NEXT).putExtra(AdcWidget.X_WID, 7));
        assertEquals("FB11", AdcWidget.prefs(c).getString("s_7", ""));
        // flèche ‹ deux fois : FB11 -> FB05 -> FB01
        new AdcWidget().onReceive(c, new Intent(c, AdcWidget.class).setAction(AdcWidget.A_PREV).putExtra(AdcWidget.X_WID, 7));
        new AdcWidget().onReceive(c, new Intent(c, AdcWidget.class).setAction(AdcWidget.A_PREV).putExtra(AdcWidget.X_WID, 7));
        assertEquals("FB01", AdcWidget.prefs(c).getString("s_7", ""));
        // ‹ encore : retour à la fin de la liste (BT01)
        new AdcWidget().onReceive(c, new Intent(c, AdcWidget.class).setAction(AdcWidget.A_PREV).putExtra(AdcWidget.X_WID, 7));
        assertEquals("BT01", AdcWidget.prefs(c).getString("s_7", ""));

        // bouton Display
        new AdcWidget().onReceive(c, new Intent(c, AdcWidget.class).setAction(AdcWidget.A_TYPE).putExtra(AdcWidget.X_WID, 7).putExtra(AdcWidget.X_KIND, "box"));
        assertEquals("box", AdcWidget.prefs(c).getString("t_7", ""));
        shot("w2_display_attente_masters", 7, 320, 196);

        // case commencée à reprendre
        AdcWidget.prefs(c).edit().putString("data", data("FB05")).putString("t_7", "case").putString("s_7", "FB05").commit();
        shot("w3_case_a_reprendre", 7, 320, 196);

        // tailles : petit et grand
        AdcWidget.prefs(c).edit().putString("t_7", "pack").commit();
        shot("w4_petit", 7, 262, 180);
        shot("w5_grand", 7, 400, 240);
        AdcWidget.updateAll(c);
    }

    @Test
    public void b_choix_de_la_serie() throws Exception {
        Context c = ctx();
        AdcWidget.prefs(c).edit().putString("data", data("")).putString("s_7", "FB05").commit();
        c.startActivity(new Intent(c, AdcPickActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(3000);
        save(InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(), "p_choix_serie");
        InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        Thread.sleep(1000);
    }

    static String js(ActivityScenario<MainActivity> sc, final String code) throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final AtomicReference<String> r = new AtomicReference<>("(pas de réponse)");
        sc.onActivity(a -> {
            WebView w = a.getBridge().getWebView();
            w.evaluateJavascript(code, v -> {
                r.set(v);
                l.countDown();
            });
        });
        l.await(20, TimeUnit.SECONDS);
        return r.get();
    }

    @Test
    public void c_ouvrir_depuis_le_widget() throws Exception {
        Context c = ctx();
        AdcWidget.prefs(c).edit().remove("data").commit();
        Intent i = new Intent(c, MainActivity.class).setAction(AdcWidget.A_OPEN + ".7")
                .putExtra(AdcWidget.X_SERIE, "FB05").putExtra(AdcWidget.X_TYPE, "box");
        StringBuilder rep = new StringBuilder();
        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(i)) {
            Thread.sleep(4000);
            JSObject p = AdcWidgetPlugin.peek();
            assertNotNull("l'appli n'a pas reçu l'appui du widget", p);
            assertEquals("FB05", p.getString("serie"));
            assertEquals("box", p.getString("type"));
            // attendre que le site soit chargé
            String ready = "";
            for (int k = 0; k < 30; k++) {
                ready = js(sc, "(function(){return document.readyState+' '+(!!window.Capacitor)+' '+location.host})()");
                if (ready.contains("complete true")) break;
                Thread.sleep(1000);
            }
            rep.append("page : ").append(ready).append('\n');
            String t = js(sc, "(function(){var C=window.Capacitor;var a=!!(C&&C.Plugins&&C.Plugins.AdcWidget);var b=!!(C&&C.registerPlugin);return 'Plugins.AdcWidget='+a+' registerPlugin='+b})()");
            rep.append(t).append('\n');
            js(sc, "(function(){var C=window.Capacitor,P=(C.Plugins&&C.Plugins.AdcWidget)||C.registerPlugin('AdcWidget');"
                    + "P.update({data:JSON.stringify({v:'≈ 42 €',n:'7 cartes',series:[['FB02','Blazing Aura','fw']],cd:{pack:0,box:0,case:0},pend:{}})})"
                    + ".then(function(r){window.__u=JSON.stringify(r)},function(e){window.__u='erreur '+e});"
                    + "P.getPending().then(function(r){window.__p=JSON.stringify(r)},function(e){window.__p='erreur '+e});return 1})()");
            Thread.sleep(2500);
            String u = js(sc, "window.__u"), g = js(sc, "window.__p");
            rep.append("update -> ").append(u).append('\n').append("getPending -> ").append(g).append('\n');
            String saved = AdcWidget.prefs(c).getString("data", "");
            rep.append("données reçues par le widget : ").append(saved).append('\n');
            save(InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(), "m_appli");
            note("rapport_js", rep.toString());
            assertTrue("update n'a pas répondu : " + u, u.contains("ok"));
            assertTrue("le widget n'a pas reçu les données", saved.contains("42"));
            assertTrue("getPending : " + g, g.contains("FB05") && g.contains("box"));
        } finally {
            note("rapport_js", rep.toString());
        }
        shot("w6_donnees_de_l_appli", 9, 320, 196);
    }
}
