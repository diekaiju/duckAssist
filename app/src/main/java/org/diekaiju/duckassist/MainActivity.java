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
    private String pendingTextToPaste = null;
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
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
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
                                "  console.log('Download request for: ' + url + ' (Map found: ' + (window.blobMap !== undefined) + ')');" +
                                "  if (blob) {" +
                                "    var reader = new FileReader();" +
                                "    reader.onloadend = function() {" +
                                "      Android.processBlob(reader.result, blob.type, '" + escapedCD + "', window.location.href);" +
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
                                "          Android.processBlob(reader.result, '" + mimetype + "', '" + escapedCD + "', window.location.href);" +
                                "        };" +
                                "      }" +
                                "    };" +
                                "    xhr.onerror = function() { console.error('Blob fetch failed: CSP or not found'); };" +
                                "    xhr.send();" +
                                "  }" +
                                "})();", null);
                return;
            } else if (url.startsWith("data:")) {
                processBlob(url, mimetype, contentDisposition, url);
                return;
            }
            Uri source = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(source);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
            if (filename == null) filename = URLUtilCompat.guessFileName(url, contentDisposition, mimetype);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            Toast.makeText(this, getString(R.string.download) + " " + filename, Toast.LENGTH_SHORT).show();
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        });

        chatWebView.addJavascriptInterface(this, "Android");
        handleIntent(getIntent());
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
    }

    @JavascriptInterface
    public void processBlob(String base64Data, String mimetype, String contentDisposition, String currentUrl) {
        if (base64Data.contains(",")) {
            base64Data = base64Data.split(",")[1];
        }

        String filename = URLUtilCompat.getFilenameFromContentDisposition(contentDisposition);
        if (filename == null || filename.isEmpty()) {
            filename = URLUtilCompat.guessFileName(currentUrl, contentDisposition, mimetype);
        }

        final String finalFilename = filename;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                        outputStream.write(data);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.download) + " " + finalFilename, Toast.LENGTH_SHORT).show());
                    }
                }
            } else {
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(path, filename);
                try (FileOutputStream os = new FileOutputStream(file)) {
                    byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                    os.write(data);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.download) + " " + finalFilename, Toast.LENGTH_SHORT).show());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Blob download failed", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCacheData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String query = data.getQueryParameter("q");
                if (query != null && !query.isEmpty()) {
                    pendingTextToPaste = query;
                    chatWebView.loadUrl("https://duck.ai/");
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
                if (text != null) sharedText = text.toString();
            }

            if (sharedText != null) {
                // Act as a bridge: open using a fresh VIEW intent to trigger a new window/task
                Uri duckUri = Uri.parse("https://duck.ai/").buildUpon()
                        .appendQueryParameter("q", sharedText)
                        .build();
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, duckUri);
                viewIntent.setPackage(getPackageName()); // Ensure it opens in our app
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(viewIntent);
                finish(); // Close the bridge instance
            } else {
                chatWebView.loadUrl("https://duck.ai/");
            }
        } else {
            if (chatWebView.getUrl() == null || chatWebView.getUrl().isEmpty() || chatWebView.getUrl().equals("about:blank")) {
                chatWebView.loadUrl("https://duck.ai/");
            }
        }
    }



    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // If the user clicks any link, redirect it to the default browser
            // We return true to signify we have handled the intent.
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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
            if (pendingTextToPaste != null && !pendingTextToPaste.isEmpty()) {
                final String textToPaste = pendingTextToPaste.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                view.evaluateJavascript(
                        "setTimeout(function() {" +
                        "  document.execCommand('insertText', false, \"" + textToPaste + "\");" +
                        "}, 1000);", null);
                pendingTextToPaste = null;
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

        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
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
            if (null == mUploadMessage) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    result = new Uri[]{Uri.parse(dataString)};
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
        clearCacheData();
        if (chatWebView != null) {
            chatWebView.destroy();
        }
        super.onDestroy();
    }
}
