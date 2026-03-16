package org.diekaiju.duckassist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;

public class AssistantSession extends VoiceInteractionSession {

    public AssistantSession(Context context) {
        super(context);
    }

    @Override
    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
        super.onHandleAssist(data, structure, content);
        // We could extract text from 'structure' here if we wanted to be fancy.
        // For now, we just launch the activity.
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.setAction(Intent.ACTION_ASSIST);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        startAssistantActivity(intent);
        finish();
    }
}
