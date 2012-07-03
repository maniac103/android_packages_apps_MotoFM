package com.motorola.android.fmradio;

interface IFMRadioServiceCallback {
    void onCommandComplete(int cmd, int status, String value);
}
