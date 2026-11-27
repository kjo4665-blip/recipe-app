package com.levelup.receipts.app;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaverPlugin.class);
        registerPlugin(PayPlugin.class);
        registerPlugin(ScanPlugin.class);
        super.onCreate(savedInstanceState);
        takePay(getIntent());

        // 뒤로가기: 앱 안의 이전 화면으로. 첫 화면에서는 "종료하시겠습니까?"를 웹에서 묻는다.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView wv = getBridge() != null ? getBridge().getWebView() : null;
                if (wv == null) { finish(); return; }
                if (wv.canGoBack()) { wv.goBack(); return; }
                wv.evaluateJavascript("(function(){try{return window.__appBack?window.__appBack():false}catch(e){return false}})()", v -> {
                    if (!"true".equals(v)) finish();
                });
            }
        });
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
