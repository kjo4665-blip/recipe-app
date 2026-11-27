package com.levelup.receipts.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** 카드 승인 문자를 받으면 PayCenter로 넘긴다. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (SmsMessage m : parts) if (m != null && m.getMessageBody() != null) sb.append(m.getMessageBody());
        PayCenter.handle(context, sb.toString(), "sms");
    }
}
