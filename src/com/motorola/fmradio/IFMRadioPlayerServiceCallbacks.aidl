package com.motorola.fmradio;

interface IFMRadioPlayerServiceCallbacks {
    void onEnabled(boolean success);
    void onDisabled();

    void onTuneChanged(boolean success, int newFrequency);
    void onSeekFinished(boolean success, int newFrequency);
    void onScanUpdate(int newFrequency);
    void onScanFinished(boolean success, int newFrequency);
    void onAbortComplete(int newFrequency);
    void onError();
    void onRdsDataChanged(int frequency, String stationName, String radioText, int pty);
    void onAudioModeChanged(boolean stereo);
}
