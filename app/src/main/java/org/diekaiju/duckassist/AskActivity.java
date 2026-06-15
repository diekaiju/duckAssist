package org.diekaiju.duckassist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class AskActivity extends Activity {
    private static final String TAG = "duckAssistAsk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        finish();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/") || "application/pdf".equals(type)) {
                Uri streamUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (streamUri != null) {
                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.setAction(Intent.ACTION_SEND);
                    chatIntent.setType(type);
                    chatIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chatIntent);
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
            android.content.SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
            String suffix = prefs.getString("ask_duck_suffix", "");
            if (suffix != null && !suffix.trim().isEmpty()) {
                sharedText = sharedText + "\n\n" + suffix;
            }
            try {
                org.json.JSONObject handoffObj = new org.json.JSONObject();
                handoffObj.put("aiChatPrompt", sharedText);
                handoffObj.put("aiChatAutoPrompt", false);
                String handoffJson = handoffObj.toString();

                Uri duckUri = Uri.parse("https://duck.ai/chat").buildUpon()
                        .appendQueryParameter("q", sharedText)
                        .appendQueryParameter("handoff", handoffJson)
                        .build();

                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.setAction(Intent.ACTION_VIEW);
                chatIntent.setData(duckUri);
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chatIntent);
            } catch (org.json.JSONException e) {
                Log.e(TAG, "Error building handoff JSON", e);
                Uri duckUri = Uri.parse("https://duck.ai/chat").buildUpon()
                        .appendQueryParameter("q", sharedText)
                        .build();
                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.setAction(Intent.ACTION_VIEW);
                chatIntent.setData(duckUri);
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chatIntent);
            }
        }
    }
}
