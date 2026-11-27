package com.levelup.receipts.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "Pay", permissions = {
    @Permission(strings = { Manifest.permission.RECEIVE_SMS }, alias = "sms"),
    @Permission(strings = { "android.permission.POST_NOTIFICATIONS" }, alias = "notify")
})
public class PayPlugin extends Plugin {
    private static PayPlugin instance;
    static String launchId;

    @Override public void load() { instance = this; PayCenter.ensureChannel(getContext()); }

    static void onLaunch(String id) {
        launchId = id;
        if (instance != null) { JSObject d = new JSObject(); d.put("id", id); instance.notifyListeners("payOpen", d, true); }
    }
    static void emitChanged() { if (instance != null) instance.notifyListeners("payChanged", new JSObject()); }

    private boolean listenerOn() {
        Context c = getContext();
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(new ComponentName(c, PayNotifListener.class).flattenToString());
    }

    private JSObject status() {
        Context c = getContext();
        JSObject s = new JSObject();
        s.put("smsOn", PayCenter.prefs(c).getBoolean("sms_on", false));
        s.put("smsPerm", getPermissionState("sms") == PermissionState.GRANTED);
        s.put("notifOn", PayCenter.prefs(c).getBoolean("notif_on", false));
        s.put("notifAccess", listenerOn());
        s.put("postNotif", NotificationManagerCompat.from(c).areNotificationsEnabled());
        JSArray ig = new JSArray();
        for (String k : PayCenter.prefs(c).getStringSet("ignored", new HashSet<>())) ig.put(k);
        s.put("ignored", ig);
        s.put("count", PayCenter.pending(c).length());
        return s;
    }

    @PluginMethod public void getStatus(PluginCall call) { call.resolve(status()); }

    @PluginMethod public void setSms(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        if (on && getPermissionState("sms") != PermissionState.GRANTED) { requestPermissionForAlias("sms", call, "smsPermCb"); return; }
        PayCenter.prefs(getContext()).edit().putBoolean("sms_on", on).apply();
        askPostNotif(call);
    }

    @PermissionCallback private void smsPermCb(PluginCall call) {
        boolean ok = getPermissionState("sms") == PermissionState.GRANTED;
        PayCenter.prefs(getContext()).edit().putBoolean("sms_on", ok).apply();
        if (ok) askPostNotif(call); else call.resolve(status());
    }

    @PluginMethod public void setNotif(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        PayCenter.prefs(getContext()).edit().putBoolean("notif_on", on).apply();
        if (on && !listenerOn()) {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            if (Build.VERSION.SDK_INT >= 30) {
                i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(getContext(), PayNotifListener.class).flattenToString());
            }
            try { getActivity().startActivity(i); } catch (Exception e) { getActivity().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        }
        if (on) askPostNotif(call); else call.resolve(status());
    }

    private void askPostNotif(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notify") != PermissionState.GRANTED) { requestPermissionForAlias("notify", call, "notifyPermCb"); return; }
        call.resolve(status());
    }
    @PermissionCallback private void notifyPermCb(PluginCall call) { call.resolve(status()); }

    @PluginMethod public void pending(PluginCall call) {
        JSObject r = new JSObject();
        try { r.put("items", new JSArray(PayCenter.pending(getContext()).toString())); } catch (Exception e) { r.put("items", new JSArray()); }
        String l = launchId; launchId = null;
        if (l != null) r.put("launch", l);
        call.resolve(r);
    }

    @PluginMethod public void remove(PluginCall call) {
        String id = call.getString("id");
        if (id != null) PayCenter.remove(getContext(), id);
        call.resolve(status());
    }

    @PluginMethod public void unmute(PluginCall call) {
        String card = call.getString("card", "");
        Set<String> ig = new HashSet<>(PayCenter.prefs(getContext()).getStringSet("ignored", new HashSet<>()));
        ig.remove(card);
        PayCenter.prefs(getContext()).edit().putStringSet("ignored", ig).apply();
        call.resolve(status());
    }

    @PluginMethod public void openAppInfo(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        getActivity().startActivity(i);
        call.resolve();
    }
}
