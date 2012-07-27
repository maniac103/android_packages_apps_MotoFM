package com.motorola.fmradio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.util.Log;

public class FMMediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "FMMediaButtonReceiver";

    private static final int DOUBLE_CLICK_TIMEOUT = 500;
    private static final int MSG_DOUBLE_CLICK_TIMEOUT = 1;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DOUBLE_CLICK_TIMEOUT:
                    Context context = (Context) msg.obj;
                    String command = keycodeToCommand(msg.arg1);
                    if (command != null) {
                        startServiceForCommand(context, command);
                    }
                    break;
            }
        }
    };

    private static void startServiceForCommand(Context context, String command) {
        Intent i = new Intent(context, FMRadioPlayerService.class);
        i.setAction(FMRadioPlayerService.ACTION_FM_COMMAND);
        i.putExtra(FMRadioPlayerService.EXTRA_COMMAND, command);
        context.startService(i);
    }

    private static String keycodeToCommand(int keycode) {
        switch (keycode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return FMRadioPlayerService.COMMAND_TOGGLE_MUTE;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return FMRadioPlayerService.COMMAND_NEXT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return FMRadioPlayerService.COMMAND_PREV;
        }

        return null;
    }

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
        long eventtime = event.getEventTime();
        String command = keycodeToCommand(keycode);

        Log.v(TAG, "Got media button event: action " + action +
                " keycode " + keycode + " -> command " + command);

        if (command == null) {
            return;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (keycode == KeyEvent.KEYCODE_HEADSETHOOK &&
                    sHandler.hasMessages(MSG_DOUBLE_CLICK_TIMEOUT)) {
                Log.v(TAG, "Detected double click of headset button, sending next command");
                sHandler.removeMessages(MSG_DOUBLE_CLICK_TIMEOUT);
                startServiceForCommand(context, FMRadioPlayerService.COMMAND_NEXT);
            } else {
                Message msg = sHandler.obtainMessage(MSG_DOUBLE_CLICK_TIMEOUT, keycode, 0, context);
                sHandler.sendMessageDelayed(msg, DOUBLE_CLICK_TIMEOUT);
            }
        }

        if (isOrderedBroadcast()) {
            abortBroadcast();
        }
    }
}
