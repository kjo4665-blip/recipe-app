package com.levelup.receipts.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

/** 사진은 사진/오늘얼마, 그 밖의 파일은 다운로드/오늘얼마 폴더에 저장한다. */
@CapacitorPlugin(name = "Saver")
public class SaverPlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("name", "file");
        String mime = call.getString("mime", "application/octet-stream");
        String sub = call.getString("sub", "");
        if (data == null) { call.reject("데이터가 없어요"); return; }
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);
        boolean image = mime.startsWith("image/");
        String folder = (image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/오늘얼마" + (sub.isEmpty() ? "" : "/" + sub.replaceAll("[^0-9A-Za-z가-힣_-]", ""));
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            ContentResolver cr = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri coll = image ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                             : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = cr.insert(coll, v);
            if (uri == null) { call.reject("저장 위치를 만들지 못했어요"); return; }
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new Exception("파일을 열지 못했어요");
                os.write(bytes);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            JSObject ret = new JSObject();
            ret.put("path", folder + "/" + name);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("저장하지 못했어요: " + e.getMessage());
        }
    }

    /** 테마가 바뀔 때 상태바·내비게이션바 색을 맞춘다. */
    @PluginMethod
    public void bars(PluginCall call) {
        final String color = call.getString("color", "#FFF4D6");
        final boolean dark = Boolean.TRUE.equals(call.getBoolean("dark", false));
        getActivity().runOnUiThread(() -> {
            try {
                int c = Color.parseColor(color);
                Window w = getActivity().getWindow();
                w.getDecorView().setBackgroundColor(c);
                w.setStatusBarColor(c);
                w.setNavigationBarColor(c);
                WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, w.getDecorView());
                ic.setAppearanceLightStatusBars(!dark);
                ic.setAppearanceLightNavigationBars(!dark);
                call.resolve();
            } catch (Exception e) { call.reject(e.getMessage()); }
        });
    }
}
