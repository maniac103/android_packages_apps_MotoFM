package com.motorola.fmradio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.util.Log;

public class FMMediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "FMMediaButtonReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }

        KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null) {
            return;
        }

        int keycode = event.getKeyCode();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            String command = null;

            switch (keycode) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = FMRadioPlayerService.COMMAND_TOGGLE_MUTE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = FMRadioPlayerService.COMMAND_NEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = FMRadioPlayerService.COMMAND_PREV;
                    break;
            }

            Log.v(TAG, "Got media button down event: keycode " + keycode + " -> command " + command);
            if (command != null) {
                Intent i = new Intent(context, FMRadioPlayerService.class);
                i.setAction(FMRadioPlayerService.ACTION_FM_COMMAND);
                i.putExtra(FMRadioPlayerService.EXTRA_COMMAND, command);
                context.startService(i);
            }
        }

        if (isOrderedBroadcast()) {
            abortBroadcast();
        }
    }
}
