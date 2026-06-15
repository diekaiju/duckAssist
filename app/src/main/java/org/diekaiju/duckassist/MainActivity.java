package org.diekaiju.duckassist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import android.Manifest;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.StrictMode;

import androidx.webkit.URLUtilCompat;

import org.woheller69.freeDroidWarn.FreeDroidWarn;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    private WebView chatWebView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILE_CHOOSER_REQUEST_CODE = 1;
    private final String TAG = "duckAssist";
    private final boolean restricted = false;
    private boolean pendingVoiceChat = false;
    private Uri pendingSharedFileUri = null;

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimetype;
    private long pendingDownloadContentLength;

    private boolean isPendingBlob = false;
    private String pendingBlobData;
    private String pendingBlobMimetype;
    private String pendingBlobContentDisposition;
    private String pendingBlobCurrentUrl;

    private static final int DOWNLOAD_PERMISSION_REQUEST_CODE = 456;
    private final String BLOB_JS = "(function() {" +
            "    if (window.blobHandlerInjected) return;" +
            "    window.blobHandlerInjected = true;" +
            "    window.blobMap = window.blobMap || new Map();" +
            "    const oC = URL.createObjectURL;" +
            "    URL.createObjectURL = function(b) {" +
            "        const u = oC.call(URL, b);" +
            "        if (b instanceof Blob) window.blobMap.set(u, b);" +
            "        console.log('Blob created: ' + u);" +
            "        return u;" +
            "    };" +
            "    console.log('Blob Handler Patch Active');" +
            "})();";

    private final String VOICE_JS = "(function() {" +
            "  console.log('Voice Chat Trigger Started');" +
            "  function tryClick() {" +
            "    var sidebarPath = document.querySelector('path[d*=\"M9.41 10.125a.625.625 0 1 1 0 1.25H1.624\"]');" +
            "    var sidebarBtn = sidebarPath ? sidebarPath.closest('button, [role=\"button\"]') : null;" +
            "    if (!sidebarBtn) {" +
            "        sidebarBtn = document.querySelector('button[aria-label*=\"sidebar\"], button[aria-label*=\"Sidebar\"]');" +
            "    }" +
            "    if (sidebarBtn && sidebarBtn.offsetParent !== null) {" +
            "      console.log('Opening sidebar first...');" +
            "      sidebarBtn.click();" +
            "    }" +
            "    var allPaths = document.querySelectorAll('path[d*=\"M5.625 0c.345 0 .625.28\"]');" +
            "    for (var i = 0; i < allPaths.length; i++) {" +
            "        var btn = allPaths[i].closest('button, [role=\"button\"]');" +
            "        if (btn) {" +
            "            console.log('Voice Chat found by SVG icon!');" +
            "            btn.click();" +
            "            return true;" +
            "        }" +
            "    }" +
            "    var elements = document.querySelectorAll('button, [role=\"button\"], div > span, a');" +
            "    for (var i = 0; i < elements.length; i++) {" +
            "      var text = (elements[i].innerText || elements[i].textContent || '').trim();" +
            "      if (text.toLowerCase().includes('voice chat')) {" +
            "        console.log('Target found by text: ' + text);" +
            "        var clickTarget = elements[i];" +
            "        while (clickTarget && clickTarget.tagName !== 'BUTTON' && clickTarget.getAttribute('role') !== 'button' && clickTarget.tagName !== 'A') {" +
            "           clickTarget = clickTarget.parentElement;" +
            "        }" +
            "        if (!clickTarget) clickTarget = elements[i];" +
            "        clickTarget.click();" +
            "        return true;" +
            "      }" +
            "    }" +
            "    return false;" +
            "  }" +
            "  if (!tryClick()) {" +
            "    console.log('Waiting for buttons...');" +
            "    var observer = new MutationObserver(function(mutations, obs) {" +
            "      if (tryClick()) {" +
            "        obs.disconnect();" +
            "        clearInterval(fallbackInterval);" +
            "      }" +
            "    });" +
            "    observer.observe(document.body, { childList: true, subtree: true });" +
            "    var fallbackInterval = setInterval(tryClick, 1000);" +
            "    setTimeout(function() { " +
            "      observer.disconnect(); " +
            "      clearInterval(fallbackInterval); " +
            "      console.log('Timeout after 15s');" +
            "    }, 15000);" +
            "  }" +
            "})();";

    private final String SETTINGS_INJECT_JS = "(function() {" +
            "    if (window.duckAssistSettingsButtonInjected) return;" +
            "    function injectButton() {" +
            "        var webSettingsPath = document.querySelector('path[d^=\"M5.647 14.153\"]');" +
            "        var webSettingsBtn = webSettingsPath ? webSettingsPath.closest('button, [role=\"button\"]') : null;" +
            "        if (webSettingsBtn && !document.querySelector('.duckassist-native-settings-btn')) {" +
            "            console.log('Found web settings button, injecting our button beside it');" +
            "            var ourBtn = webSettingsBtn.cloneNode(true);" +
            "            ourBtn.classList.add('duckassist-native-settings-btn');" +
            "            var svg = ourBtn.querySelector('svg');" +
            "            if (svg) {" +
            "                svg.innerHTML = '<path fill=\"currentColor\" d=\"M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.3C.5 6.7.9 9.8 2.9 11.8c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.1z\"/>';" +
            "            }" +
            "            ourBtn.style.marginLeft = '8px';" +
            "            ourBtn.style.marginRight = '8px';" +
            "            ourBtn.addEventListener('click', function(e) {" +
            "                e.stopPropagation();" +
            "                e.preventDefault();" +
            "                if (typeof Android !== 'undefined' && Android.showSettingsDialog) {" +
            "                    Android.showSettingsDialog();" +
            "                }" +
            "            });" +
            "            webSettingsBtn.parentNode.insertBefore(ourBtn, webSettingsBtn.nextSibling);" +
            "            window.duckAssistSettingsButtonInjected = true;" +
            "            return true;" +
            "        }" +
            "        return false;" +
            "    }" +
            "    if (!injectButton()) {" +
            "        var observer = new MutationObserver(function(mutations) {" +
            "            if (injectButton()) {" +
            "                observer.disconnect();" +
            "            }" +
            "        });" +
            "        observer.observe(document.body, { childList: true, subtree: true });" +
            "    }" +
            "})();";

    private void clearCacheData() {
        if (chatWebView != null) {
            chatWebView.clearCache(true);
        }
        Log.d(TAG, "Cache cleared.");
    }

    private Uri saveUriToTempFile(Uri uri, String extension) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File tempFile = new File(getCacheDir(), "shared_file_" + System.currentTimeMillis() + extension);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
            is.close();
            return Uri.fromFile(tempFile);
        } catch (Exception e) {
            Log.e(TAG, "Error saving shared file to temp file", e);
            return null;
        }
    }

    protected void setActivityTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        }
    }

    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.VmPolicy.Builder StrictBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(StrictBuilder.build());

        setActivityTheme();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResourceId());

        progressBar = findViewById(R.id.progressBar);
        chatWebView = findViewById(R.id.chatWebView);

        WebSettings webSettings = chatWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setSaveFormData(false);
        webSettings.setGeolocationEnabled(false);

        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        int savedZoom = prefs.getInt("text_zoom", 100);
        webSettings.setTextZoom(savedZoom);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(chatWebView, true);

        chatWebView.setWebViewClient(new MyWebViewClient());
        chatWebView.setWebChromeClient(new MyWebChromeClient());

        chatWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) {
                String escapedCD = contentDisposition != null ? contentDisposition.replace("'", "\\'") : "";
                chatWebView.evaluateJavascript(
                        "(function() {" +
                                "  var url = '" + url + "';" +
                                "  var blob = window.blobMap ? window.blobMap.get(url) : null;" +
                                "  console.log('Download request for: ' + url + ' (Map found: ' + (window.blobMap !== undefined) + ')');"
                                +
                                "  if (blob) {" +
                                "    var reader = new FileReader();" +
                                "    reader.onloadend = function() {" +
                                "      Android.processBlob(reader.result, blob.type, '" + escapedCD
                                + "', window.location.href);" +
                                "    };" +
                                "    reader.readAsDataURL(blob);" +
                                "  } else {" +
                                "    console.warn('Blob not found in map, trying XHR fallback...');" +
                                "    var xhr = new XMLHttpRequest();" +
                                "    xhr.open('GET', url, true);" +
                                "    xhr.responseType = 'blob';" +
                                "    xhr.onload = function() {" +
                                "      if (this.status == 200) {" +
                                "        var reader = new FileReader();" +
                                "        reader.readAsDataURL(this.response);" +
                                "        reader.onloadend = function() {" +
                                "          Android.processBlob(reader.result, '" + mimetype + "', '" + escapedCD
                                + "', window.location.href);" +
                                "        };" +
                                "      }" +
                                "    };" +
                                "    xhr.onerror = function() { console.error('Blob fetch failed: CSP or not found'); };"
                                +
                                "    xhr.send();" +
                                "  }" +
                                "})();",
                        null);
                return;
            } else if (url.startsWith("data:")) {
                processBlob(url, mimetype, contentDisposition, url);
                return;
            }
            if (checkDownloadPermissions()) {
                startStandardDownload(url, userAgent, contentDisposition, mimetype, contentLength);
            } else {
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimetype = mimetype;
                pendingDownloadContentLength = contentLength;
                isPendingBlob = false;
            }
        });

        chatWebView.addJavascriptInterface(this, "Android");

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                int currentZoom = chatWebView.getSettings().getTextZoom();
                int newZoom = (int) (currentZoom * scale);
                // Clamp text zoom between 50% and 300%
                newZoom = Math.max(50, Math.min(newZoom, 300));
                chatWebView.getSettings().setTextZoom(newZoom);
                
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                prefs.edit().putInt("text_zoom", newZoom).apply();
                return true;
            }
        });

        chatWebView.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                return false;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 123);
            }
        }

        handleIntent(getIntent());
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
    }

    @JavascriptInterface
    public void processBlob(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
        if (checkDownloadPermissions()) {
            saveBlobToFile(base64Data, mimetype, contentDisposition, currentUrl);
        } else {
            isPendingBlob = true;
            pendingBlobData = base64Data;
            pendingBlobMimetype = mimetype;
            pendingBlobContentDisposition = contentDisposition;
            pendingBlobCurrentUrl = currentUrl;
        }
    }

    @JavascriptInterface
    public void showSettingsDialog() {
        runOnUiThread(() -> {
            android.app.Dialog dialog = new android.app.Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            WebView webView = new WebView(MainActivity.this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            
            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String getSettingsJson() {
                    SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                    org.json.JSONObject obj = new org.json.JSONObject();
                    try {
                        obj.put("use_drawer_assistant", prefs.getBoolean("use_drawer_assistant", true));
                        obj.put("use_drawer_shared", prefs.getBoolean("use_drawer_shared", true));
                        obj.put("trigger_voice_assistant", prefs.getBoolean("trigger_voice_assistant", true));
                        obj.put("ask_duck_suffix", prefs.getString("ask_duck_suffix", ""));
                        obj.put("shared_doc_suffix", prefs.getString("shared_doc_suffix", ""));
                    } catch (Exception e) {
                        Log.e(TAG, "Error generating settings JSON", e);
                    }
                    return obj.toString();
                }

                @JavascriptInterface
                public void saveSettings(String jsonStr) {
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                        prefs.edit()
                             .putBoolean("use_drawer_assistant", obj.getBoolean("use_drawer_assistant"))
                             .putBoolean("use_drawer_shared", obj.getBoolean("use_drawer_shared"))
                             .putBoolean("trigger_voice_assistant", obj.getBoolean("trigger_voice_assistant"))
                             .putString("ask_duck_suffix", obj.getString("ask_duck_suffix"))
                             .putString("shared_doc_suffix", obj.getString("shared_doc_suffix"))
                             .apply();
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving settings", e);
                    }
                }

                @JavascriptInterface
                public void dismissSettings() {
                    runOnUiThread(() -> dialog.dismiss());
                }
            }, "AndroidSettings");

            webView.loadUrl("file:///android_asset/settings.html");
            
            dialog.setContentView(webView);
            dialog.show();
            
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
        });
    }

    private void saveBlobToFile(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
        if (base64Data.contains(",")) {
            base64Data = base64Data.split(",")[1];
        }

        String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
        if (filename == null || filename.isEmpty()) {
            filename = URLUtilCompat.guessFileName(currentUrl, contentDisposition, mimetype);
        }

        final String finalFilename = filename;
        try {
            Uri fileUri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "duck.ai");

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                        outputStream.write(data);
                        fileUri = uri;
                    }
                }
            } else {
                File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "duck.ai");
                if (!path.exists()) {
                    path.mkdirs();
                }
                File file = new File(path, filename);
                try (FileOutputStream os = new FileOutputStream(file)) {
                    byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                    os.write(data);
                    fileUri = Uri.fromFile(file);
                }
                // Force media scanner to scan the file so it shows in downloads library
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{mimetype}, null);
            }

            if (fileUri != null) {
                final Uri finalUri = fileUri;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, getString(R.string.download) + " " + finalFilename, Toast.LENGTH_SHORT).show();
                    showDownloadNotification(finalFilename, mimetype, finalUri);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Blob download failed", e);
        }
    }

    private void startStandardDownload(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        Uri source = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(source);
        request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
        if (filename == null)
            filename = URLUtilCompat.guessFileName(url, contentDisposition, mimetype);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "duck.ai" + File.separator + filename);
        Toast.makeText(this, getString(R.string.download) + " " + filename, Toast.LENGTH_SHORT).show();
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null)
            dm.enqueue(request);
    }

    private boolean checkDownloadPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, DOWNLOAD_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void showDownloadNotification(String filename, String mimeType, Uri uri) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "duck_downloads";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType != null ? mimeType : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(filename)
                .setContentText("Download completed")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_PERMISSION_REQUEST_CODE) {
            boolean writeGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                    writeGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            if (writeGranted) {
                if (isPendingBlob) {
                    if (pendingBlobData != null) {
                        saveBlobToFile(pendingBlobData, pendingBlobMimetype, pendingBlobContentDisposition, pendingBlobCurrentUrl);
                    }
                } else {
                    if (pendingDownloadUrl != null) {
                        startStandardDownload(pendingDownloadUrl, pendingDownloadUserAgent, pendingDownloadContentDisposition, pendingDownloadMimetype, pendingDownloadContentLength);
                    }
                }
            } else {
                Toast.makeText(this, "Permission denied. Cannot download file.", Toast.LENGTH_SHORT).show();
            }
            clearPendingDownload();
        }
    }

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimetype = null;
        pendingDownloadContentLength = 0;
        isPendingBlob = false;
        pendingBlobData = null;
        pendingBlobMimetype = null;
        pendingBlobContentDisposition = null;
        pendingBlobCurrentUrl = null;
    }

    private void showCustomBanner(String message) {
        runOnUiThread(() -> {
            View root = findViewById(android.R.id.content);
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup viewGroup = (android.view.ViewGroup) root;
                
                View oldBanner = viewGroup.findViewWithTag("attention_banner");
                if (oldBanner != null) {
                    viewGroup.removeView(oldBanner);
                }
                
                android.widget.LinearLayout bannerCard = new android.widget.LinearLayout(this);
                bannerCard.setTag("attention_banner");
                bannerCard.setOrientation(android.widget.LinearLayout.VERTICAL);
                
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                shape.setColor(Color.parseColor("#d32f2f"));
                shape.setCornerRadius(12 * getResources().getDisplayMetrics().density);
                bannerCard.setBackground(shape);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bannerCard.setElevation(16 * getResources().getDisplayMetrics().density);
                }
                
                android.widget.TextView textView = new android.widget.TextView(this);
                textView.setText(message);
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(16);
                textView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                int padding = (int) (16 * getResources().getDisplayMetrics().density);
                textView.setPadding(padding, padding, padding, padding);
                textView.setGravity(android.view.Gravity.CENTER);
                
                bannerCard.addView(textView);
                
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                );
                int margin = (int) (16 * getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin + (int)(24 * getResources().getDisplayMetrics().density), margin, margin);
                lp.gravity = android.view.Gravity.TOP;
                
                bannerCard.setTranslationY(-300);
                viewGroup.addView(bannerCard, lp);
                
                bannerCard.animate()
                        .translationY(0)
                        .setDuration(400)
                        .setInterpolator(new android.view.animation.OvershootInterpolator())
                        .start();
                
                bannerCard.postDelayed(() -> {
                    bannerCard.animate()
                            .translationY(-400)
                            .setDuration(300)
                            .withEndAction(() -> viewGroup.removeView(bannerCard))
                            .start();
                }, 6000);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        //clearCacheData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        String type = intent.getType();
        Uri data = intent.getData();
        Log.d(TAG, "handleIntent: action = " + action + ", type = " + type + ", data = " + (data != null ? data.toString() : "null"));
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Log.d(TAG, "  extra: " + key + " = " + intent.getExtras().get(key));
            }
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            data = intent.getData();
            if (data != null) {
                String query = data.getQueryParameter("q");
                if (query != null && !query.isEmpty()) {
                    try {
                        org.json.JSONObject handoffObj = new org.json.JSONObject();
                        handoffObj.put("aiChatPrompt", query);
                        handoffObj.put("aiChatAutoPrompt", false);
                        String handoffJson = handoffObj.toString();
                        
                        Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                                .appendQueryParameter("q", query)
                                .appendQueryParameter("handoff", handoffJson);
                        chatWebView.loadUrl(builder.build().toString());
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error building handoff JSON", e);
                        chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(query));
                    }
                } else {
                    chatWebView.loadUrl(data.toString());
                }
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
        } else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_PROCESS_TEXT.equals(action)) {
            String sharedText = null;
            if (type != null && (type.startsWith("image/") || "application/pdf".equals(type))) {
                Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (streamUri != null) {
                    if (type.startsWith("image/")) {
                        String ext = ".jpg";
                        if (type.contains("png")) ext = ".png";
                        else if (type.contains("webp")) ext = ".webp";
                        pendingSharedFileUri = saveUriToTempFile(streamUri, ext);
                    } else if ("application/pdf".equals(type)) {
                        pendingSharedFileUri = saveUriToTempFile(streamUri, ".pdf");
                    }
                }
            } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (text != null)
                    sharedText = text.toString();
            }

            if (pendingSharedFileUri != null) {
                showCustomBanner("Tap 📎 to attach the shared file");
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                String docSuffix = prefs.getString("shared_doc_suffix", "");
                if (docSuffix != null && !docSuffix.trim().isEmpty()) {
                    try {
                        org.json.JSONObject handoffObj = new org.json.JSONObject();
                        handoffObj.put("aiChatPrompt", docSuffix);
                        handoffObj.put("aiChatAutoPrompt", false);
                        String handoffJson = handoffObj.toString();
                        
                        Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                                .appendQueryParameter("q", docSuffix)
                                .appendQueryParameter("handoff", handoffJson);
                        chatWebView.loadUrl(builder.build().toString());
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error building handoff JSON", e);
                        chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(docSuffix));
                    }
                } else {
                    chatWebView.loadUrl("https://duck.ai/chat");
                }
            } else if (sharedText != null) {
                SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                String suffix = prefs.getString("ask_duck_suffix", "");
                if (suffix != null && !suffix.trim().isEmpty()) {
                    sharedText = sharedText + "\n\n" + suffix;
                }
                try {
                    org.json.JSONObject handoffObj = new org.json.JSONObject();
                    handoffObj.put("aiChatPrompt", sharedText);
                    handoffObj.put("aiChatAutoPrompt", false);
                    String handoffJson = handoffObj.toString();
                    
                    Uri.Builder builder = Uri.parse("https://duck.ai/chat").buildUpon()
                            .appendQueryParameter("q", sharedText)
                            .appendQueryParameter("handoff", handoffJson);
                    chatWebView.loadUrl(builder.build().toString());
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error building handoff JSON", e);
                    chatWebView.loadUrl("https://duck.ai/chat?q=" + Uri.encode(sharedText));
                }
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
        } else if (Intent.ACTION_ASSIST.equals(action)) {
            Log.d(TAG, "Assistance shortcut triggered");
            SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            boolean triggerVoice = prefs.getBoolean("trigger_voice_assistant", true);
            if (triggerVoice) {
                String currentUrl = chatWebView.getUrl();
                if (currentUrl != null && currentUrl.startsWith("https://duck.ai")) {
                    chatWebView.evaluateJavascript(VOICE_JS, null);
                } else {
                    pendingVoiceChat = true;
                    chatWebView.loadUrl("https://duck.ai/");
                }
            } else {
                if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty()
                        || chatWebView.getUrl().equals("about:blank")) {
                    chatWebView.loadUrl("https://duck.ai/");
                }
            }
        } else {
            if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty()
                    || chatWebView.getUrl().equals("about:blank")) {
                chatWebView.loadUrl("https://duck.ai/");
            }
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            
            // Allow duck.ai and duckduckgo.com links to load inside the app
            if (host != null && (host.endsWith("duck.ai") || host.endsWith("duckduckgo.com"))) {
                return false;
            }

            // Redirect all other external links to the default browser
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            view.evaluateJavascript(BLOB_JS, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            view.evaluateJavascript(BLOB_JS, null);
            view.evaluateJavascript(SETTINGS_INJECT_JS, null);
            if (pendingVoiceChat) {
                view.evaluateJavascript(
                        "setTimeout(function() {" +
                                VOICE_JS +
                                "}, 1500);",
                        null);
                pendingVoiceChat = false;
            }
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (newProgress > 5) {
                view.evaluateJavascript(BLOB_JS, null);
                view.evaluateJavascript(SETTINGS_INJECT_JS, null);
            }
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            Log.d(TAG, "JS Console: " + consoleMessage.message() + " (Line " + consoleMessage.lineNumber() + ")");
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            MainActivity.this.runOnUiThread(() -> {
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        request.grant(new String[] { resource });
                        return;
                    }
                }
                request.deny();
            });
        }

        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            if (pendingSharedFileUri != null) {
                filePathCallback.onReceiveValue(new Uri[] { pendingSharedFileUri });
                pendingSharedFileUri = null;
                return true;
            }
            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
            }
            mUploadMessage = filePathCallback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "File Chooser"), FILE_CHOOSER_REQUEST_CODE);
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (null == mUploadMessage)
                return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    result = new Uri[] { Uri.parse(dataString) };
                }
            }
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (chatWebView.canGoBack()) {
                    chatWebView.goBack();
                } else {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        //clearCacheData();
        if (chatWebView != null) {
            chatWebView.destroy();
        }
        super.onDestroy();
    }
}
