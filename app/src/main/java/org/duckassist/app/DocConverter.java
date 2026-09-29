package org.duckassist.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DocConverter {
    private static final String TAG = "duckAssistDocConverter";

    private static final String PRIMARY_WASM_URL = "https://unpkg.com/@firecrawl/anydoc-wasm@0.2.4/anydoc_wasm_bg.wasm";
    private static final String FALLBACK_WASM_URL = "https://cdn.jsdelivr.net/npm/@firecrawl/anydoc-wasm@0.2.4/anydoc_wasm_bg.wasm";
    private static final String WASM_FILE_NAME = "anydoc_wasm_bg.wasm";
    public static final int MAX_PAGES_PER_PDF_PART = 15;

    private static final Set<String> DOC_EXTENSIONS = new HashSet<>(Arrays.asList(
            "doc", "docx", "docm", "odt", "rtf", "epub",
            "ppt", "pps", "pot", "pptx", "pptm", "ppsx", "ppsm", "odp",
            "xls", "xlsx", "xlsm", "xlsb", "ods", "csv"
    ));

    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "md", "markdown", "json", "xml", "html", "htm", "log",
            "js", "ts", "py", "java", "c", "cpp", "h", "css", "yaml", "yml",
            "sh", "bat", "ini", "conf", "toml", "sql"
    ));

    public interface ConversionCallback {
        void onStatusUpdate(String status);
        void onSuccess(String markdown);
        void onError(String errorMessage);
    }

    public interface DocumentProcessingCallback {
        void onStatusUpdate(String status);
        void onTextReady(String text);
        void onPdfReady(List<Uri> pdfUris, String originalName);
        void onError(String errorMessage);
    }

    private interface WasmReadyCallback {
        void onReady();
        void onError(String error);
    }

    private static DocConverter instance;

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor;

    private WebView runnerWebView;
    private boolean isPageLoaded = false;
    private boolean isWasmLoaded = false;
    private boolean isWasmLoading = false;
    private String wasmLoadError = null;
    private String pendingWasmBase64 = null;

    private final List<WasmReadyCallback> wasmReadyCallbacks = new ArrayList<>();
    private final AtomicLong reqIdCounter = new AtomicLong(1);
    private final Map<String, ConversionCallback> pendingCallbacks = new ConcurrentHashMap<>();

    private DocConverter(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newCachedThreadPool();
    }

    public static synchronized DocConverter getInstance(Context context) {
        if (instance == null) {
            instance = new DocConverter(context);
        }
        return instance;
    }

    public static boolean isDocExtension(String ext) {
        if (ext == null) return false;
        return DOC_EXTENSIONS.contains(ext.toLowerCase().trim().replace(".", ""));
    }

    public static boolean isPlainTextExtension(String ext) {
        if (ext == null) return false;
        return TEXT_EXTENSIONS.contains(ext.toLowerCase().trim().replace(".", ""));
    }

    public static boolean isSupportedDoc(Context context, Uri uri, String mimeType) {
        if (uri == null) return false;
        String ext = getFileExtension(context, uri, mimeType);
        return isDocExtension(ext);
    }

    public static boolean isPlainTextDoc(Context context, Uri uri, String mimeType) {
        if (uri == null) return false;
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType.equals("application/json") || mimeType.equals("application/xml"))) {
            String ext = getFileExtension(context, uri, mimeType);
            if ("csv".equalsIgnoreCase(ext)) {
                return false; // Process CSV via anydoc for table conversion
            }
            return true;
        }
        String ext = getFileExtension(context, uri, mimeType);
        return isPlainTextExtension(ext);
    }

    public static String getFileName(Context context, Uri uri) {
        if (uri == null) return "document";
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get display name from cursor", e);
            }
        }
        String path = uri.getPath();
        if (path != null) {
            int cut = path.lastIndexOf('/');
            if (cut != -1) {
                return path.substring(cut + 1);
            }
            return path;
        }
        return "document";
    }

    public static String getFileExtension(Context context, Uri uri, String mimeType) {
        String name = getFileName(context, uri);
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            if (!ext.isEmpty()) {
                return ext;
            }
        }
        if (mimeType != null) {
            if (mimeType.contains("wordprocessingml") || mimeType.contains("docx")) return "docx";
            if (mimeType.contains("msword")) return "doc";
            if (mimeType.contains("spreadsheetml") || mimeType.contains("xlsx")) return "xlsx";
            if (mimeType.contains("ms-excel")) return "xls";
            if (mimeType.contains("presentationml") || mimeType.contains("pptx")) return "pptx";
            if (mimeType.contains("ms-powerpoint")) return "ppt";
            if (mimeType.contains("opendocument.text") || mimeType.contains("odt")) return "odt";
            if (mimeType.contains("opendocument.spreadsheet") || mimeType.contains("ods")) return "ods";
            if (mimeType.contains("opendocument.presentation") || mimeType.contains("odp")) return "odp";
            if (mimeType.contains("epub")) return "epub";
            if (mimeType.contains("rtf")) return "rtf";
            if (mimeType.contains("csv")) return "csv";
            if (mimeType.contains("pdf")) return "pdf";
            if (mimeType.contains("markdown")) return "md";
            if (mimeType.startsWith("text/")) return "txt";
        }
        return "";
    }

    public static String readPlainText(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    public static List<Uri> createPdfPartsFromText(Context context, String title, String content) throws IOException {
        if (content == null) content = "";
        List<Uri> resultUris = new ArrayList<>();

        // Standard A4 dimensions in points (72 points per inch): 595 x 842
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 40;
        int printableWidth = Math.max(100, pageWidth - (margin * 2));
        int printableHeight = Math.max(100, pageHeight - (margin * 2));

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(11f);
        textPaint.setAntiAlias(true);

        StaticLayout layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(content, 0, content.length(), textPaint, printableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.25f)
                    .setIncludePad(false)
                    .build();
        } else {
            layout = new StaticLayout(content, textPaint, printableWidth,
                    Layout.Alignment.ALIGN_NORMAL, 1.25f, 0f, false);
        }

        int lineCount = layout.getLineCount();
        if (lineCount == 0) {
            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            document.finishPage(page);
            File cacheFile = new File(context.getCacheDir(), "doc_" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                document.writeTo(fos);
            } finally {
                document.close();
            }
            resultUris.add(FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", cacheFile));
            return resultUris;
        }

        int lineIndex = 0;
        int partIndex = 1;
        long timestamp = System.currentTimeMillis();

        while (lineIndex < lineCount) {
            PdfDocument document = new PdfDocument();
            int pageInCurrentPart = 1;

            try {
                while (lineIndex < lineCount && pageInCurrentPart <= MAX_PAGES_PER_PDF_PART) {
                    int startLine = lineIndex;
                    int startY = layout.getLineTop(startLine);
                    int endY = startY + printableHeight;

                    // Find last line that fits
                    int endLine = startLine;
                    while (endLine < lineCount && layout.getLineBottom(endLine) <= endY) {
                        endLine++;
                    }
                    if (endLine == startLine) {
                        endLine = startLine + 1;
                    }

                    PdfDocument.PageInfo pageInfo =
                            new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageInCurrentPart).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    canvas.save();
                    canvas.translate(margin, margin - startY);
                    canvas.clipRect(0, startY, printableWidth, layout.getLineBottom(endLine - 1));
                    layout.draw(canvas);
                    canvas.restore();

                    document.finishPage(page);

                    lineIndex = endLine;
                    pageInCurrentPart++;
                }

                String baseName = (title != null && !title.isEmpty()) ? title.replaceAll("[^a-zA-Z0-9_.-]", "_") : "doc";
                if (baseName.endsWith(".pdf") || baseName.endsWith(".PDF")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String fileName = baseName + (partIndex > 1 || lineIndex < lineCount ? "_part" + partIndex : "") + "_" + timestamp + ".pdf";
                File cacheFile = new File(context.getCacheDir(), fileName);
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    document.writeTo(fos);
                }
                resultUris.add(FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", cacheFile));
                partIndex++;

                if (partIndex > 20) {
                    break;
                }
            } finally {
                document.close();
            }
        }

        return resultUris;
    }

    public static List<Uri> splitNativePdfIfNeeded(Context context, Uri pdfUri, String originalName) {
        List<Uri> result = new ArrayList<>();
        if (pdfUri == null) return result;

        try {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(pdfUri, "r");
            if (pfd == null) {
                result.add(pdfUri);
                return result;
            }

            PdfRenderer renderer = new PdfRenderer(pfd);
            int pageCount = renderer.getPageCount();
            if (pageCount <= MAX_PAGES_PER_PDF_PART) {
                renderer.close();
                pfd.close();
                result.add(pdfUri);
                return result;
            }

            String baseName = (originalName != null && !originalName.isEmpty()) ? originalName.replaceAll("[^a-zA-Z0-9_.-]", "_") : "doc";
            if (baseName.endsWith(".pdf") || baseName.endsWith(".PDF")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            long timestamp = System.currentTimeMillis();
            int partIndex = 1;

            for (int i = 0; i < pageCount; i += MAX_PAGES_PER_PDF_PART) {
                int partEnd = Math.min(i + MAX_PAGES_PER_PDF_PART, pageCount);
                PdfDocument doc = new PdfDocument();
                int pageInPart = 1;

                for (int p = i; p < partEnd; p++) {
                    PdfRenderer.Page page = renderer.openPage(p);
                    int w = page.getWidth();
                    int h = page.getHeight();

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(w, h, pageInPart).create();
                    PdfDocument.Page docPage = doc.startPage(pageInfo);

                    float scale = 2.0f;
                    int bw = Math.max(1, (int) (w * scale));
                    int bh = Math.max(1, (int) (h * scale));
                    Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                    Canvas canvas = docPage.getCanvas();
                    Rect src = new Rect(0, 0, bw, bh);
                    Rect dst = new Rect(0, 0, w, h);
                    canvas.drawBitmap(bitmap, src, dst, null);
                    bitmap.recycle();

                    doc.finishPage(docPage);
                    page.close();
                    pageInPart++;
                }

                String fileName = baseName + "_part" + partIndex + "_" + timestamp + ".pdf";
                File cacheFile = new File(context.getCacheDir(), fileName);
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    doc.writeTo(fos);
                } finally {
                    doc.close();
                }

                result.add(FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", cacheFile));
                partIndex++;
            }

            renderer.close();
            pfd.close();
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error splitting native PDF", e);
            result.clear();
            result.add(pdfUri);
            return result;
        }
    }

    public void processNativePdf(Uri uri, DocumentProcessingCallback callback) {
        executor.execute(() -> {
            mainHandler.post(() -> callback.onStatusUpdate("Checking PDF page count..."));
            String filename = getFileName(context, uri);
            try {
                List<Uri> parts = splitNativePdfIfNeeded(context, uri, filename);
                if (parts.size() > 1) {
                    mainHandler.post(() -> callback.onStatusUpdate("Split into " + parts.size() + " parts (15 pages max each)..."));
                }
                mainHandler.post(() -> callback.onPdfReady(parts, filename));
            } catch (Exception e) {
                Log.e(TAG, "Error processing native PDF", e);
                mainHandler.post(() -> callback.onError("Error reading PDF: " + e.getMessage()));
            }
        });
    }

    public void processDocument(Uri uri, String mimeType, DocumentProcessingCallback callback) {
        executor.execute(() -> {
            mainHandler.post(() -> callback.onStatusUpdate("Reading document..."));

            String filename = getFileName(context, uri);

            if (isPlainTextDoc(context, uri, mimeType)) {
                try {
                    String text = readPlainText(context, uri);
                    if (text == null || text.trim().isEmpty()) {
                        mainHandler.post(() -> callback.onError("Document is empty"));
                        return;
                    }

                    if (text.length() <= 1500) {
                        mainHandler.post(() -> callback.onTextReady(text));
                    } else {
                        mainHandler.post(() -> callback.onStatusUpdate("Generating PDF attachment (split into 15 pages max)..."));
                        List<Uri> pdfUris = createPdfPartsFromText(context, filename, text);
                        mainHandler.post(() -> callback.onPdfReady(pdfUris, filename));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing plain text document", e);
                    mainHandler.post(() -> callback.onError("Error reading text: " + e.getMessage()));
                }
                return;
            }

            if (isSupportedDoc(context, uri, mimeType)) {
                convertDocument(uri, filename, new ConversionCallback() {
                    @Override
                    public void onStatusUpdate(String status) {
                        callback.onStatusUpdate(status);
                    }

                    @Override
                    public void onSuccess(String markdown) {
                        if (markdown == null || markdown.trim().isEmpty()) {
                            callback.onError("Converted document is empty");
                            return;
                        }

                        if (markdown.length() <= 1500) {
                            callback.onTextReady(markdown);
                        } else {
                            callback.onStatusUpdate("Generating PDF attachment (split into 15 pages max)...");
                            executor.execute(() -> {
                                try {
                                    List<Uri> pdfUris = createPdfPartsFromText(context, filename, markdown);
                                    mainHandler.post(() -> callback.onPdfReady(pdfUris, filename));
                                } catch (Exception e) {
                                    Log.e(TAG, "Error generating PDF from markdown", e);
                                    mainHandler.post(() -> callback.onTextReady(markdown));
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        callback.onError(errorMessage);
                    }
                });
                return;
            }

            mainHandler.post(() -> callback.onError("Unsupported file format"));
        });
    }

    private void initWebView() {
        if (runnerWebView != null) return;
        try {
            runnerWebView = new WebView(context);
            WebSettings settings = runnerWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setDomStorageEnabled(true);

            runnerWebView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void onPageLoaded() {
                    mainHandler.post(() -> {
                        isPageLoaded = true;
                        if (pendingWasmBase64 != null) {
                            runnerWebView.evaluateJavascript("window.initConverter('" + pendingWasmBase64 + "');", null);
                            pendingWasmBase64 = null;
                        }
                    });
                }

                @JavascriptInterface
                public void onWasmReady(boolean success, String error) {
                    mainHandler.post(() -> {
                        isWasmLoading = false;
                        if (success) {
                            isWasmLoaded = true;
                            wasmLoadError = null;
                            List<WasmReadyCallback> cbs = new ArrayList<>(wasmReadyCallbacks);
                            wasmReadyCallbacks.clear();
                            for (WasmReadyCallback cb : cbs) {
                                cb.onReady();
                            }
                        } else {
                            isWasmLoaded = false;
                            wasmLoadError = error;
                            List<WasmReadyCallback> cbs = new ArrayList<>(wasmReadyCallbacks);
                            wasmReadyCallbacks.clear();
                            for (WasmReadyCallback cb : cbs) {
                                cb.onError(error);
                            }
                        }
                    });
                }

                @JavascriptInterface
                public void onConversionResult(String reqId, boolean success, String result) {
                    mainHandler.post(() -> {
                        ConversionCallback cb = pendingCallbacks.remove(reqId);
                        if (cb != null) {
                            if (success) {
                                cb.onSuccess(result);
                            } else {
                                cb.onError(result);
                            }
                        }
                    });
                }
            }, "DocBridge");

            runnerWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    isPageLoaded = true;
                    if (pendingWasmBase64 != null) {
                        runnerWebView.evaluateJavascript("window.initConverter('" + pendingWasmBase64 + "');", null);
                        pendingWasmBase64 = null;
                    }
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    Log.e(TAG, "WebView runner error: " + error);
                }
            });

            runnerWebView.loadUrl("file:///android_asset/converter.html");
        } catch (Exception e) {
            Log.e(TAG, "Error creating runner WebView", e);
        }
    }

    private void ensureWasmReady(ConversionCallback statusCb, WasmReadyCallback callback) {
        initWebView();

        if (isWasmLoaded) {
            if (callback != null) callback.onReady();
            return;
        }

        if (callback != null) {
            wasmReadyCallbacks.add(callback);
        }

        if (isWasmLoading) {
            return;
        }

        File wasmFile = new File(context.getFilesDir(), WASM_FILE_NAME);
        if (wasmFile.exists() && wasmFile.length() > 100000) {
            if (statusCb != null) {
                statusCb.onStatusUpdate("Loading converter...");
            }
            loadLocalWasm(wasmFile);
        } else {
            if (statusCb != null) {
                statusCb.onStatusUpdate("Downloading converter...");
            }
            downloadWasm(wasmFile);
        }
    }

    private void loadLocalWasm(File wasmFile) {
        isWasmLoading = true;
        executor.execute(() -> {
            try {
                byte[] wasmBytes;
                try (FileInputStream fis = new FileInputStream(wasmFile);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                    wasmBytes = bos.toByteArray();
                }

                String wasmBase64 = Base64.encodeToString(wasmBytes, Base64.NO_WRAP);
                mainHandler.post(() -> {
                    if (runnerWebView != null && isPageLoaded) {
                        runnerWebView.evaluateJavascript("window.initConverter('" + wasmBase64 + "');", null);
                    } else {
                        pendingWasmBase64 = wasmBase64;
                    }
                });
            } catch (Exception e) {
                isWasmLoading = false;
                Log.e(TAG, "Error loading local wasm file", e);
                mainHandler.post(() -> {
                    List<WasmReadyCallback> cbs = new ArrayList<>(wasmReadyCallbacks);
                    wasmReadyCallbacks.clear();
                    for (WasmReadyCallback cb : cbs) {
                        cb.onError("Error loading converter: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void downloadWasm(File destFile) {
        isWasmLoading = true;
        executor.execute(() -> {
            boolean success = tryDownload(PRIMARY_WASM_URL, destFile);
            if (!success) {
                Log.w(TAG, "Primary WASM download failed, trying fallback...");
                success = tryDownload(FALLBACK_WASM_URL, destFile);
            }

            if (success) {
                loadLocalWasm(destFile);
            } else {
                isWasmLoading = false;
                mainHandler.post(() -> {
                    List<WasmReadyCallback> cbs = new ArrayList<>(wasmReadyCallbacks);
                    wasmReadyCallbacks.clear();
                    for (WasmReadyCallback cb : cbs) {
                        cb.onError("Could not download document converter. Please check your internet connection.");
                    }
                });
            }
        });
    }

    private boolean tryDownload(String urlString, File destFile) {
        File tempFile = new File(context.getCacheDir(), WASM_FILE_NAME + ".tmp");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "WASM download HTTP code: " + code);
                return false;
            }

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            if (tempFile.length() > 100000) {
                if (destFile.exists()) {
                    destFile.delete();
                }
                return tempFile.renameTo(destFile);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error downloading WASM from " + urlString, e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
            if (tempFile.exists()) tempFile.delete();
        }
    }

    public void convertDocument(Uri uri, String formatOrPath, ConversionCallback callback) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> callback.onStatusUpdate("Reading document..."));

                byte[] fileBytes;
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    if (is == null) {
                        mainHandler.post(() -> callback.onError("Failed to open file stream"));
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                    fileBytes = bos.toByteArray();
                }

                if (fileBytes == null || fileBytes.length == 0) {
                    mainHandler.post(() -> callback.onError("Document is empty"));
                    return;
                }

                String fileBase64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
                String reqId = String.valueOf(reqIdCounter.getAndIncrement());
                pendingCallbacks.put(reqId, callback);

                mainHandler.post(() -> {
                    callback.onStatusUpdate("Preparing converter...");
                    ensureWasmReady(callback, new WasmReadyCallback() {
                        @Override
                        public void onReady() {
                            callback.onStatusUpdate("Converting document to Markdown...");
                            String formatArg = formatOrPath != null ? formatOrPath : "";
                            String js = String.format("window.convertDocument('%s', '%s', '%s');",
                                    reqId, fileBase64, formatArg);
                            runnerWebView.evaluateJavascript(js, null);

                            // Safeguard timeout of 60 seconds
                            mainHandler.postDelayed(() -> {
                                ConversionCallback timedOutCb = pendingCallbacks.remove(reqId);
                                if (timedOutCb != null) {
                                    timedOutCb.onError("Document conversion timed out");
                                }
                            }, 60000);
                        }

                        @Override
                        public void onError(String error) {
                            pendingCallbacks.remove(reqId);
                            callback.onError(error);
                        }
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing file for conversion", e);
                mainHandler.post(() -> callback.onError("Error reading file: " + e.getMessage()));
            }
        });
    }

    public static class ProgressDialogController {
        private final AlertDialog dialog;
        private final TextView messageView;

        public ProgressDialogController(AlertDialog dialog, TextView messageView) {
            this.dialog = dialog;
            this.messageView = messageView;
        }

        public void setMessage(String message) {
            if (messageView != null) {
                messageView.setText(message);
            }
        }

        public void dismiss() {
            try {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception ignored) {}
        }
    }

    public static ProgressDialogController showProgressDialog(Activity activity, String initialMessage, Runnable onCancel) {
        if (activity == null || activity.isFinishing()) {
            return new ProgressDialogController(null, null);
        }

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(progressBar, pbParams);

        TextView textView = new TextView(activity);
        textView.setText(initialMessage != null ? initialMessage : "Converting document...");
        textView.setTextSize(16);
        int textMarginLeft = (int) (18 * activity.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.setMargins(textMarginLeft, 0, 0, 0);
        textView.setLayoutParams(textParams);
        layout.addView(textView);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(layout);
        builder.setCancelable(true);
        builder.setOnCancelListener(dialog -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        try {
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing progress dialog", e);
        }

        return new ProgressDialogController(dialog, textView);
    }
}
