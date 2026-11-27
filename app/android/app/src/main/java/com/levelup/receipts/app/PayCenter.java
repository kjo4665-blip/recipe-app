package com.levelup.receipts.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 결제 문자·알림을 받아 확인 대기 목록에 쌓고 "추가할까요?" 알림을 띄운다. 원문은 저장하지 않는다. */
public final class PayCenter {
    static final String PREFS = "pay";
    static final String CHANNEL = "pay";
    static final String EXTRA_ID = "payId";
    static final String ACTION_IGNORE = "com.levelup.receipts.app.PAY_IGNORE";
    static final String ACTION_MUTE = "com.levelup.receipts.app.PAY_MUTE";

    private PayCenter() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static synchronized void handle(Context ctx, String text, String source) {
        SharedPreferences p = prefs(ctx);
        if ("sms".equals(source) && !p.getBoolean("sms_on", false)) return;
        if ("notif".equals(source) && !p.getBoolean("notif_on", false)) return;
        PayParser.Info i = PayParser.parse(text);
        if (i == null) return;
        String cardKey = (i.company + " " + i.card).trim();
        if (!i.card.isEmpty() && p.getStringSet("ignored", new HashSet<>()).contains(cardKey)) return;
        try {
            JSONArray recent = new JSONArray(p.getString("recent", "[]"));
            String key = i.key();
            for (int k = 0; k < recent.length(); k++) if (key.equals(recent.optString(k))) return;
            recent.put(key);
            while (recent.length() > 60) recent.remove(0);

            JSONObject o = new JSONObject();
            o.put("id", UUID.randomUUID().toString().substring(0, 12));
            o.put("kind", i.kind); o.put("company", i.company); o.put("card", i.card);
            o.put("amount", i.amount); o.put("date", i.date); o.put("time", i.time);
            o.put("place", i.place); o.put("install", i.install); o.put("source", source);
            o.put("at", System.currentTimeMillis());
            JSONArray pend = new JSONArray(p.getString("pending", "[]"));
            pend.put(o);
            while (pend.length() > 100) pend.remove(0);
            p.edit().putString("recent", recent.toString()).putString("pending", pend.toString()).apply();
            notifyItem(ctx, o);
        } catch (Exception ignored) { }
    }

    static synchronized JSONArray pending(Context ctx) {
        try { return new JSONArray(prefs(ctx).getString("pending", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void remove(Context ctx, String id) {
        JSONArray pend = pending(ctx), out = new JSONArray();
        for (int k = 0; k < pend.length(); k++) { JSONObject o = pend.optJSONObject(k); if (o != null && !id.equals(o.optString("id"))) out.put(o); }
        prefs(ctx).edit().putString("pending", out.toString()).apply();
        NotificationManagerCompat.from(ctx).cancel(id.hashCode());
    }

    static synchronized void mute(Context ctx, String id) {
        JSONArray pend = pending(ctx);
        String cardKey = null;
        for (int k = 0; k < pend.length(); k++) { JSONObject o = pend.optJSONObject(k); if (o != null && id.equals(o.optString("id"))) cardKey = (o.optString("company") + " " + o.optString("card")).trim(); }
        if (cardKey == null || cardKey.isEmpty()) { remove(ctx, id); return; }
        Set<String> ig = new HashSet<>(prefs(ctx).getStringSet("ignored", new HashSet<>()));
        ig.add(cardKey);
        JSONArray out = new JSONArray();
        for (int k = 0; k < pend.length(); k++) {
            JSONObject o = pend.optJSONObject(k); if (o == null) continue;
            String ck = (o.optString("company") + " " + o.optString("card")).trim();
            if (cardKey.equals(ck)) NotificationManagerCompat.from(ctx).cancel(o.optString("id").hashCode()); else out.put(o);
        }
        prefs(ctx).edit().putStringSet("ignored", ig).putString("pending", out.toString()).apply();
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "결제 알림", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("카드 결제를 가계부에 추가할지 물어봐요");
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static void notifyItem(Context ctx, JSONObject o) {
        ensureChannel(ctx);
        String id = o.optString("id");
        int nid = id.hashCode();
        String won = NumberFormat.getNumberInstance(Locale.KOREA).format(o.optLong("amount")) + "원";
        String place = o.optString("place");
        boolean cancel = "cancel".equals(o.optString("kind"));
        String title = (cancel ? "결제 취소 · " : "") + (place.isEmpty() ? "카드 결제" : place) + " " + won;
        String card = (o.optString("company") + " " + o.optString("card")).trim();
        String when = o.optString("date").length() >= 10 ? o.optString("date").substring(5).replace('-', '/') + " " + o.optString("time") : "";
        String body = (cancel ? "가계부에서 찾아 정리할까요?" : "가계부에 추가할까요?") + "  " + card + (when.isEmpty() ? "" : " · " + when);

        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(ctx, MainActivity.class).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(ctx, nid, open, fl);
        PendingIntent piIgnore = PendingIntent.getBroadcast(ctx, nid + 1, new Intent(ctx, PayActionReceiver.class).setAction(ACTION_IGNORE).putExtra(EXTRA_ID, id), fl);
        PendingIntent piMute = PendingIntent.getBroadcast(ctx, nid + 2, new Intent(ctx, PayActionReceiver.class).setAction(ACTION_MUTE).putExtra(EXTRA_ID, id), fl);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pay)
            .setColor(0xFFF76707)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(piOpen)
            .setAutoCancel(true)
            .addAction(0, cancel ? "열기" : "추가", piOpen)
            .addAction(0, "무시", piIgnore);
        if (!card.isEmpty() && !o.optString("card").isEmpty()) b.addAction(0, "이 카드는 묻지 않기", piMute);
        try { NotificationManagerCompat.from(ctx).notify(nid, b.build()); } catch (SecurityException ignored) { }
    }
}
