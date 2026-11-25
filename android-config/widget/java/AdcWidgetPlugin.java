package com.appeldescartes.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Pont entre l'appli (le site) et le widget :
 *  - update({data}) : l'appli envoie ses chiffres (valeur, cartes, séries, temps d'attente) au widget ;
 *  - getPending()   : l'appli demande si on vient d'appuyer sur « Ouvrir » dans le widget ;
 *  - événement « open » quand l'appli était déjà ouverte.
 */
@CapacitorPlugin(name = "AdcWidget")
public class AdcWidgetPlugin extends Plugin {
    private static JSObject pending = null;

    static synchronized boolean take(Intent it) {
        if (it == null) return false;
        String serie = it.getStringExtra(AdcWidget.X_SERIE);
        if (serie == null || serie.isEmpty()) return false;
        String type = it.getStringExtra(AdcWidget.X_TYPE);
        JSObject o = new JSObject();
        o.put("serie", serie);
        o.put("type", type == null ? "pack" : type);
        o.put("t", System.currentTimeMillis());
        pending = o;
        it.removeExtra(AdcWidget.X_SERIE);
        it.removeExtra(AdcWidget.X_TYPE);
        return true;
    }

    static synchronized JSObject peek() {
        return pending;
    }

    static synchronized JSObject pop() {
        JSObject p = pending;
        pending = null;
        return p;
    }

    @Override
    public void load() {
        Activity a = getActivity();
        if (a != null) take(a.getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (take(intent)) {
            JSObject p = peek();
            if (p != null) notifyListeners("open", p, true);
        }
    }

    @PluginMethod
    public void update(PluginCall call) {
        String data = call.getString("data");
        if (data == null || data.isEmpty()) {
            call.reject("data manquant");
            return;
        }
        Context c = getContext();
        AdcWidget.prefs(c).edit().putString("data", data).apply();
        AdcWidget.updateAll(c);
        JSObject r = new JSObject();
        r.put("ok", true);
        r.put("widgets", AdcWidget.count(c));
        call.resolve(r);
    }

    @PluginMethod
    public void getPending(PluginCall call) {
        JSObject p = pop();
        call.resolve(p == null ? new JSObject() : p);
    }
}
