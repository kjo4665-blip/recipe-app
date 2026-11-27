package com.levelup.receipts.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 구글 문서 스캐너(폰 안에서 동작, 무료)로 영수증을 스캔해 JPEG로 돌려준다. */
@CapacitorPlugin(name = "Scan")
public class ScanPlugin extends Plugin {
    private ActivityResultLauncher<IntentSenderRequest> launcher;
    private PluginCall pending;

    @Override
    public void load() {
        launcher = getActivity().registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), res -> {
            PluginCall call = pending; pending = null;
            if (call == null) return;
            if (res.getResultCode() != Activity.RESULT_OK) { call.reject("canceled", "CANCELED"); return; }
            GmsDocumentScanningResult r = GmsDocumentScanningResult.fromActivityResultIntent(res.getData());
            if (r == null || r.getPages() == null || r.getPages().isEmpty()) { call.reject("canceled", "CANCELED"); return; }
            new Thread(() -> {
                JSArray out = new JSArray();
                for (GmsDocumentScanningResult.Page pg : r.getPages()) {
                    String b64 = encode(pg.getImageUri());
                    if (b64 != null) out.put("data:image/jpeg;base64," + b64);
                }
                JSObject ret = new JSObject();
                ret.put("images", out);
                call.resolve(ret);
            }).start();
        });
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (pending != null) { call.reject("busy"); return; }
        int limit = Math.max(1, Math.min(20, call.getInt("limit", 10)));
        GmsDocumentScannerOptions opts = new GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(limit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(opts);
        pending = call;
        scanner.getStartScanIntent(getActivity())
            .addOnSuccessListener(sender -> launcher.launch(new IntentSenderRequest.Builder(sender).build()))
            .addOnFailureListener(e -> { PluginCall c = pending; pending = null; if (c != null) c.reject("unavailable: " + e.getMessage(), "UNAVAILABLE"); });
    }

    /** 긴 변이 2000px를 넘지 않게 줄여 JPEG로 만든다. */
    private String encode(Uri uri) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options(); b.inJustDecodeBounds = true;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, b); }
            int s = 1; while (Math.max(b.outWidth, b.outHeight) / (s * 2) >= 2000) s *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = s;
            Bitmap bm;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) { bm = BitmapFactory.decodeStream(in, null, o); }
            if (bm == null) return null;
            int w = bm.getWidth(), h = bm.getHeight(), m = Math.max(w, h);
            if (m > 2000) { Bitmap sc = Bitmap.createScaledBitmap(bm, w * 2000 / m, h * 2000 / m, true); bm.recycle(); bm = sc; }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 88, os); bm.recycle();
            return Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }
}
