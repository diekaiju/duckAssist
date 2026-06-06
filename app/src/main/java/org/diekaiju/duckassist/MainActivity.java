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
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;

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

    private void clearCacheData() {
        if (chatWebView != null) {
            chatWebView.clearCache(true);
        }
        Log.d(TAG, "Cache cleared.");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (text != null)
                    sharedText = text.toString();
            }

            if (sharedText != null) {
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
