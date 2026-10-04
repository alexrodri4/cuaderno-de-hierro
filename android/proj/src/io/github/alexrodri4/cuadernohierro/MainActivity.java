package io.github.alexrodri4.cuadernohierro;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private static final String BASE = "https://cuaderno-de-hierro.app/";
  private static final int PICK = 7, PERM = 8, MIC = 10, VOICE_ACT = 11, CAM = 12, GAL = 13, STEPS = 14;
  private FrameLayout root;
  private int photoId = -1;
  private Uri photoUri;
  private SpeechRecognizer sr;
  private WebView web;
  private ValueCallback<Uri[]> fileCb;
  private boolean loaded = false;
  private String pendingAction = null;

  public class Bridge {
    @JavascriptInterface public void sync(String json) {
      Core.prefs(MainActivity.this).edit().putString("data", json).apply();
      Core.scheduleDaily(MainActivity.this);
      CdhWidget.updateAll(MainActivity.this);
    }
    @JavascriptInterface public void scheduleRest(double ms, String label) { Core.scheduleRest(MainActivity.this, (long) ms, label); }
    @JavascriptInterface public void cancelRest() { Core.cancelRest(MainActivity.this); Core.cancel(MainActivity.this, Core.N_REST); }
    @JavascriptInterface public boolean notificationsAllowed() { return Core.allowed(MainActivity.this); }
    @JavascriptInterface public void requestPermission(boolean force) { runOnUiThread(() -> askPermission(force)); }
    // ---------- Claude ----------
    @JavascriptInterface public boolean hasApiKey() { return !Core.prefs(MainActivity.this).getString("apiKey", "").isEmpty(); }
    @JavascriptInterface public String apiKeyHint() {
      String k = Core.prefs(MainActivity.this).getString("apiKey", "");
      return k.length() > 12 ? k.substring(0, 10) + "…" + k.substring(k.length() - 4) : "";
    }
    @JavascriptInterface public void clearApiKey() { Core.prefs(MainActivity.this).edit().remove("apiKey").apply(); CdhWidget.updateAll(MainActivity.this); }
    @JavascriptInterface public String saveKeyFromClipboard() {
      final String[] out = {"empty"};
      final Object lock = new Object();
      runOnUiThread(() -> {
        try {
          ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData cd = cm.getPrimaryClip();
          String t = (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).coerceToText(MainActivity.this) != null)
            ? cd.getItemAt(0).coerceToText(MainActivity.this).toString().trim() : "";
          if (t.isEmpty()) out[0] = "empty";
          else if (!t.startsWith("sk-ant-") || t.length() < 40 || t.contains(" ")) out[0] = "invalid";
          else { Core.prefs(MainActivity.this).edit().putString("apiKey", t).apply(); out[0] = "ok"; CdhWidget.updateAll(MainActivity.this); }
        } catch (Exception e) { out[0] = "empty"; }
        synchronized (lock) { lock.notifyAll(); }
      });
      synchronized (lock) { try { lock.wait(3000); } catch (InterruptedException e) { } }
      return out[0];
    }
    @JavascriptInterface public void testKey(int id) { http(id, "GET", "https://api.anthropic.com/v1/models?limit=1", null); }
    @JavascriptInterface public void ask(int id, String body) { http(id, "POST", "https://api.anthropic.com/v1/messages", body); }

    // ---------- voz ----------
    @JavascriptInterface public void startListening() { runOnUiThread(() -> listen()); }
    @JavascriptInterface public void stopListening() { runOnUiThread(() -> { if (sr != null) sr.stopListening(); }); }
    @JavascriptInterface public void cancelListening() { runOnUiThread(() -> { if (sr != null) { sr.cancel(); } }); }

    // ---------- barras y cámara ----------
    @JavascriptInterface public String stepsJson() { return Steps.json(MainActivity.this); }
    @JavascriptInterface public int stepsState() { return !Steps.available(MainActivity.this) ? 0 : !Steps.permitted(MainActivity.this) ? 1 : 2; }
    @JavascriptInterface public void stepsEnable() { runOnUiThread(() -> enableSteps()); }
    @JavascriptInterface public void stepsRefresh() { runOnUiThread(() -> Steps.sample(MainActivity.this, () -> stepsCb())); }
    @JavascriptInterface public void setBars(String hex, boolean light) { runOnUiThread(() -> applyBars(hex, light)); }
    @JavascriptInterface public void takePhoto(int id, boolean camera) { runOnUiThread(() -> photo(id, camera)); }

    @JavascriptInterface public void test() {
      Core.notify(MainActivity.this, Core.N_TEST, Core.CH_REMIND, "FOKUS", "Las notificaciones funcionan correctamente.", 0xFF35F29A, Core.openApp(MainActivity.this, null, 40), null, null);
    }
  }

  private void http(final int id, final String method, final String url, final String body) {
    final String key = Core.prefs(this).getString("apiKey", "");
    new Thread(() -> {
      int code = 0; String resp;
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000); c.setReadTimeout(90000);
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        c.setRequestProperty("content-type", "application/json");
        if (body != null) {
          c.setDoOutput(true);
          OutputStream os = c.getOutputStream(); os.write(body.getBytes("UTF-8")); os.close();
        }
        code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close(); }
        resp = bo.toString("UTF-8");
      } catch (Exception e) {
        code = 0; resp = "{\"error\":{\"message\":\"" + e.getClass().getSimpleName() + "\"}}";
      }
      final String js = "window.__cdhAi&&window.__cdhAi(" + id + "," + JSONObject.quote(resp) + "," + code + ")";
      runOnUiThread(() -> web.evaluateJavascript(js, null));
    }).start();
  }

  @SuppressWarnings("deprecation")
  private void applyBars(String hex, boolean light) {
    try {
      int c = Color.parseColor(hex);
      root.setBackgroundColor(c);
      getWindow().setStatusBarColor(c);
      getWindow().setNavigationBarColor(c);
      if (Build.VERSION.SDK_INT >= 30) {
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
          int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
          ic.setSystemBarsAppearance(light ? m : 0, m);
        }
      } else {
        int f = getWindow().getDecorView().getSystemUiVisibility();
        int m = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        getWindow().getDecorView().setSystemUiVisibility(light ? (f | m) : (f & ~m));
      }
    } catch (Exception e) { }
  }

  private void photoResult(String b64, String err) {
    final String js = "window.__cdhPhoto&&window.__cdhPhoto(" + photoId + "," + JSONObject.quote(b64 == null ? "" : b64) + "," + JSONObject.quote(err == null ? "" : err) + ")";
    runOnUiThread(() -> web.evaluateJavascript(js, null));
  }

  private void photo(int id, boolean camera) {
    photoId = id; photoUri = null;
    if (!camera) {
      Intent g = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
      try { startActivityForResult(Intent.createChooser(g, "Elegir foto"), GAL); } catch (Exception e) { photoResult(null, "sin galería"); }
      return;
    }
    Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "cdh_" + System.currentTimeMillis() + ".jpg");
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CuadernoDeHierro");
        photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (photoUri != null) { i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
      }
      startActivityForResult(i, CAM);
    } catch (Exception e) { cleanupPhoto(); photoResult(null, "sin cámara"); }
  }

  private void cleanupPhoto() {
    if (photoUri != null) { try { getContentResolver().delete(photoUri, null, null); } catch (Exception e) { } photoUri = null; }
  }

  private void processPhoto(final Uri uri, final Bitmap thumb, final boolean deleteAfter) {
    new Thread(() -> {
      try {
        Bitmap bm = thumb; int rot = 0;
        if (uri != null) {
          BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
          InputStream in = getContentResolver().openInputStream(uri); BitmapFactory.decodeStream(in, null, o); in.close();
          int s = 1; while (Math.max(o.outWidth, o.outHeight) / s > 2200) s *= 2;
          BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = s;
          in = getContentResolver().openInputStream(uri); bm = BitmapFactory.decodeStream(in, null, o2); in.close();
          try {
            in = getContentResolver().openInputStream(uri);
            int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); in.close();
            rot = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
          } catch (Exception e) { }
        }
        if (bm == null) { photoResult(null, "foto vacía"); return; }
        float k = Math.min(1f, 1280f / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix mx = new Matrix(); mx.postScale(k, k); if (rot != 0) mx.postRotate(rot);
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), mx, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 82, bo);
        photoResult(Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP), null);
      } catch (Exception e) { photoResult(null, e.getClass().getSimpleName()); }
      finally { if (deleteAfter) runOnUiThread(this::cleanupPhoto); }
    }).start();
  }

  private void voice(String ev, String text) {
    final String js = "window.__cdhVoice&&window.__cdhVoice(" + JSONObject.quote(ev) + "," + JSONObject.quote(text == null ? "" : text) + ")";
    runOnUiThread(() -> web.evaluateJavascript(js, null));
  }

  private Intent recogIntent() {
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué quieres hacer");
    return i;
  }

  private void listen() {
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC);
      return;
    }
    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      try { startActivityForResult(recogIntent(), VOICE_ACT); } catch (Exception e) { voice("error", "sin reconocimiento de voz"); }
      return;
    }
    if (sr == null) {
      sr = SpeechRecognizer.createSpeechRecognizer(this);
      sr.setRecognitionListener(new RecognitionListener() {
        public void onReadyForSpeech(Bundle p) { voice("ready", ""); }
        public void onBeginningOfSpeech() { }
        public void onRmsChanged(float rms) { voice("rms", String.valueOf(rms)); }
        public void onBufferReceived(byte[] b) { }
        public void onEndOfSpeech() { }
        public void onError(int e) {
          String m = e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "nomatch"
            : e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "perm"
            : e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ? "sin conexión" : "código " + e;
          voice("error", m);
        }
        public void onResults(Bundle r) {
          ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          voice("final", l != null && !l.isEmpty() ? l.get(0) : "");
        }
        public void onPartialResults(Bundle r) {
          ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if (l != null && !l.isEmpty()) voice("partial", l.get(0));
        }
        public void onEvent(int t, Bundle p) { }
      });
    }
    try { sr.cancel(); sr.startListening(recogIntent()); } catch (Exception e) { voice("error", "micrófono ocupado"); }
  }

  private void stepsCb() { runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.__cdhSteps&&window.__cdhSteps()", null); }); }
  private void enableSteps() {
    if (!Steps.available(this)) return;
    if (!Steps.permitted(this)) { requestPermissions(new String[]{"android.permission.ACTIVITY_RECOGNITION"}, STEPS); return; }
    Steps.schedule(this); Steps.sample(this, () -> stepsCb());
  }
  @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
    if (req == STEPS) { if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) { Steps.schedule(this); Steps.sample(this, () -> stepsCb()); } stepsCb(); return; }
    if (req == MIC) {
      if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) listen(); else voice("error", "perm");
    }
  }

  @Override protected void onDestroy() { if (sr != null) sr.destroy(); super.onDestroy(); }

  private void askPermission(boolean force) {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
      boolean asked = Core.prefs(this).getBoolean("askedPerm", false);
      if (!asked || shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
        Core.prefs(this).edit().putBoolean("askedPerm", true).apply();
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PERM);
        return;
      }
    }
    if (force && !Core.allowed(this)) {
      try {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
      } catch (Exception e) { }
    }
  }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    Core.channels(this);
    Steps.schedule(this);
    web = new WebView(this);
    web.setBackgroundColor(Color.parseColor("#080C0E"));
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(false);
    s.setTextZoom(100);
    web.addJavascriptInterface(new Bridge(), "CdhNative");
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        if (u.toString().startsWith(BASE)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
        return true;
      }
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        String u = r.getUrl().toString();
        if (!u.startsWith(BASE) || u.length() <= BASE.length()) return null;
        String path = u.substring(BASE.length()); int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
        if (!(path.startsWith("img/") || path.startsWith("fonts/"))) return null;
        String mime = path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".png") ? "image/png" : "image/jpeg";
        try {
          android.webkit.WebResourceResponse res = new android.webkit.WebResourceResponse(mime, null, getAssets().open(path));
          java.util.HashMap<String,String> h = new java.util.HashMap<>(); h.put("Access-Control-Allow-Origin", "*"); h.put("Cache-Control", "max-age=31536000"); res.setResponseHeaders(h);
          return res;
        } catch (Exception e) { return null; }
      }
      @Override public void onPageFinished(WebView v, String url) {
        loaded = true;
        if (pendingAction != null) { runAction(pendingAction); pendingAction = null; }
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(Intent.createChooser(i, "Elegir imagen"), PICK); }
        catch (Exception e) { fileCb = null; return false; }
        return true;
      }
    });
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.parseColor("#080C0E"));
    root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    root.setOnApplyWindowInsetsListener((v, ins) -> {
      int top, bottom, left, right;
      if (Build.VERSION.SDK_INT >= 30) {
        android.graphics.Insets sb = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        android.graphics.Insets ime = ins.getInsets(WindowInsets.Type.ime());
        top = sb.top; left = sb.left; right = sb.right; bottom = Math.max(sb.bottom, ime.bottom);
      } else {
        top = ins.getSystemWindowInsetTop(); bottom = ins.getSystemWindowInsetBottom(); left = ins.getSystemWindowInsetLeft(); right = ins.getSystemWindowInsetRight();
      }
      v.setPadding(left, top, right, bottom);
      return ins;
    });
    setContentView(root);
    pendingAction = getIntent() != null ? getIntent().getStringExtra("cdh_action") : null;
    try {
      InputStream in = getAssets().open("index.html");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[16384]; int n;
      while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
      in.close();
      web.loadDataWithBaseURL(BASE, out.toString("UTF-8"), "text/html", "UTF-8", null);
    } catch (Exception e) {
      web.loadData("No se pudo abrir la app", "text/plain", "UTF-8");
    }
    askPermission(false);
  }

  private void runAction(String a) {
    if ("start".equals(a)) web.evaluateJavascript("window.__cdhStartNext&&window.__cdhStartNext()", null);
    if ("voice".equals(a)) web.evaluateJavascript("window.__cdhVoiceStart&&window.__cdhVoiceStart()", null);
  }

  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    String a = i.getStringExtra("cdh_action");
    if (a == null) return;
    if (loaded) runAction(a); else pendingAction = a;
  }

  @Override protected void onResume() { super.onResume(); Core.visible = true; Core.cancel(this, Core.N_REST); Steps.sample(this, () -> stepsCb()); }
  @Override protected void onPause() { super.onPause(); Core.visible = false; }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == CAM) {
      if (res != RESULT_OK) { cleanupPhoto(); photoResult(null, "cancel"); return; }
      Bitmap thumb = null;
      if (data != null && data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) thumb = (Bitmap) data.getExtras().get("data");
      if (photoUri != null) processPhoto(photoUri, null, true); else processPhoto(null, thumb, false);
      return;
    }
    if (req == GAL) {
      if (res != RESULT_OK || data == null || data.getData() == null) { photoResult(null, "cancel"); return; }
      processPhoto(data.getData(), null, false);
      return;
    }
    if (req == VOICE_ACT) {
      ArrayList<String> l = data != null ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
      if (res == RESULT_OK && l != null && !l.isEmpty()) voice("final", l.get(0)); else voice("error", "nomatch");
      return;
    }
    if (req == PICK && fileCb != null) {
      Uri[] r = null;
      if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{ data.getData() };
      fileCb.onReceiveValue(r);
      fileCb = null;
    }
  }

  @Override public void onBackPressed() {
    web.evaluateJavascript("(window.__cdhBack&&window.__cdhBack())?'1':'0'", v -> {
      if (!"\"1\"".equals(v)) MainActivity.super.onBackPressed();
    });
  }
}
