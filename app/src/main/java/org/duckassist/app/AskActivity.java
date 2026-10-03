package org.duckassist.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AskActivity extends Activity {
    private static final String TAG = "duckAssistAsk";

    private DocConverter.ProgressDialogController progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (streamUri != null) {
                android.content.SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
                boolean useNewUpload = prefs.getBoolean("use_new_upload", true);

                // 1. Fallback for native images or when normal upload is selected
                if (!useNewUpload || (type != null && type.startsWith("image/"))) {
                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.setAction(Intent.ACTION_SEND);
                    chatIntent.setType(type);
                    chatIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                    chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chatIntent);
                    finish();
                    return;
                }

                // 2. Native PDF check (check if exceeds 15 pages and split if needed)
                String ext = DocConverter.getFileExtension(this, streamUri, type);
                if ("pdf".equalsIgnoreCase(ext) || "application/pdf".equals(type) || (type != null && type.contains("pdf"))) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    progressDialog = DocConverter.showProgressDialog(this, "Checking " + filename + "...", this::finish);
                    DocConverter.getInstance(this).processNativePdf(streamUri, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            if (progressDialog != null) {
                                progressDialog.setMessage(status);
                            }
                        }

                        @Override
                        public void onTextReady(String text) {}

                        @Override
                        public void onPdfReady(List<Uri> pdfUris, String originalName) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            if (pdfUris == null || pdfUris.isEmpty()) {
                                finish();
                                return;
                            }
                            Intent chatIntent = new Intent(AskActivity.this, ChatActivity.class);
                            if (pdfUris.size() == 1) {
                                chatIntent.setAction(Intent.ACTION_SEND);
                                chatIntent.putExtra(Intent.EXTRA_STREAM, pdfUris.get(0));
                            } else {
                                chatIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                chatIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(pdfUris));
                            }
                            chatIntent.setType("application/pdf");
                            chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chatIntent);
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            Log.e(TAG, "Native PDF processing failed: " + errorMessage);
                            Toast.makeText(AskActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }

                // 3. Process convertible documents or plain text files
                if (DocConverter.isSupportedDoc(this, streamUri, type) || DocConverter.isPlainTextDoc(this, streamUri, type)) {
                    String filename = DocConverter.getFileName(this, streamUri);
                    progressDialog = DocConverter.showProgressDialog(this, "Processing " + filename + "...", this::finish);
                    DocConverter.getInstance(this).processDocument(streamUri, type, new DocConverter.DocumentProcessingCallback() {
                        @Override
                        public void onStatusUpdate(String status) {
                            if (progressDialog != null) {
                                progressDialog.setMessage(status);
                            }
                        }

                        @Override
                        public void onTextReady(String text) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            launchDuckChatWithText(text);
                            finish();
                        }

                        @Override
                        public void onPdfReady(List<Uri> pdfUris, String originalName) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            if (pdfUris == null || pdfUris.isEmpty()) {
                                finish();
                                return;
                            }
                            Intent chatIntent = new Intent(AskActivity.this, ChatActivity.class);
                            if (pdfUris.size() == 1) {
                                chatIntent.setAction(Intent.ACTION_SEND);
                                chatIntent.putExtra(Intent.EXTRA_STREAM, pdfUris.get(0));
                            } else {
                                chatIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                chatIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(pdfUris));
                            }
                            chatIntent.setType("application/pdf");
                            chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chatIntent);
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            Log.e(TAG, "Document processing failed: " + errorMessage);
                            Toast.makeText(AskActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }
            }
        }

        String sharedText = null;

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (text != null) {
                sharedText = text.toString();
            }
        }

        if (sharedText != null) {
            launchDuckChatWithText(sharedText);
        }
        finish();
    }

    private void launchDuckChatWithText(String promptText) {
        if (promptText == null || promptText.trim().isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        String suffix = prefs.getString("shared_doc_suffix", "");
        if (suffix == null || suffix.trim().isEmpty()) {
            suffix = prefs.getString("ask_duck_suffix", "");
        }

        String fullPrompt = promptText;
        if (suffix != null && !suffix.trim().isEmpty()) {
            fullPrompt = promptText + "\n\n" + suffix;
        }

        try {
            JSONObject handoffObj = new JSONObject();
            handoffObj.put("aiChatPrompt", fullPrompt);
            handoffObj.put("aiChatAutoPrompt", false);
            String handoffJson = handoffObj.toString();

            Uri duckUri = Uri.parse("https://duck.ai/chat").buildUpon()
                    .appendQueryParameter("q", fullPrompt)
                    .appendQueryParameter("handoff", handoffJson)
                    .build();

            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.setAction(Intent.ACTION_VIEW);
            chatIntent.setData(duckUri);
            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chatIntent);
        } catch (JSONException e) {
            Log.e(TAG, "Error building handoff JSON", e);
            Uri duckUri = Uri.parse("https://duck.ai/chat").buildUpon()
                    .appendQueryParameter("q", fullPrompt)
                    .build();
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.setAction(Intent.ACTION_VIEW);
            chatIntent.setData(duckUri);
            chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chatIntent);
        }
    }

    @Override
    protected void onDestroy() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }
}
