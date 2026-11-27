package com.levelup.receipts.app;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaverPlugin.class);
        registerPlugin(PayPlugin.class);
        super.onCreate(savedInstanceState);
        takePay(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        takePay(intent);
    }

    private void takePay(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(PayCenter.EXTRA_ID);
        if (id == null) return;
        intent.removeExtra(PayCenter.EXTRA_ID);
        PayPlugin.onLaunch(id);
        androidx.core.app.NotificationManagerCompat.from(this).cancel(id.hashCode());
    }
}
