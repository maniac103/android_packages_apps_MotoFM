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
    private static final int MSG_HSH_DOUBLE_CLICK_TIMEOUT = 1;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_HSH_DOUBLE_CLICK_TIMEOUT:
                    Context context = (Context) msg.obj;
                    startServiceForCommand(context, FMRadioPlayerService.COMMAND_TOGGLE_MUTE);
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

        Log.v(TAG, "Got media button event: action " + action +
                " keycode " + keycode + " -> command " + command);

        if (command == null) {
            return;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (keycode == KeyEvent.KEYCODE_HEADSETHOOK) {
                if (sHandler.hasMessages(MSG_HSH_DOUBLE_CLICK_TIMEOUT)) {
                    Log.v(TAG, "Detected double click of headset button, sending next command");
                    sHandler.removeMessages(MSG_HSH_DOUBLE_CLICK_TIMEOUT);
                    startServiceForCommand(context, FMRadioPlayerService.COMMAND_NEXT);
                } else {
                    Message msg = sHandler.obtainMessage(MSG_HSH_DOUBLE_CLICK_TIMEOUT, context);
                    sHandler.sendMessageDelayed(msg, DOUBLE_CLICK_TIMEOUT);
                }
            } else {
                startServiceForCommand(context, command);
            }
        }

        if (isOrderedBroadcast()) {
            abortBroadcast();
        }
    }
}
