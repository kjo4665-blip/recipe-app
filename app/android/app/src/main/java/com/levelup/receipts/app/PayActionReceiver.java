package com.levelup.receipts.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 결제 알림의 [무시] / [이 카드는 묻지 않기] 버튼. */
public class PayActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(PayCenter.EXTRA_ID);
        if (id == null) return;
        if (PayCenter.ACTION_MUTE.equals(intent.getAction())) PayCenter.mute(context, id);
        else PayCenter.remove(context, id);
        PayPlugin.emitChanged();
    }
}
