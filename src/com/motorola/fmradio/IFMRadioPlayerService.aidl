package com.motorola.fmradio;

import com.motorola.fmradio.IFMRadioPlayerServiceCallbacks;

interface IFMRadioPlayerService {
    boolean powerOn(int freq);
    boolean powerOff();
    boolean isPowerOn();

    boolean tune(int freq);
    boolean seek(int freq, int direction);
    boolean stopSeek();

    boolean scan();
    boolean stopScan();

    int getAudioMode();

    boolean setAudioRouting(int mode);
    int getAudioRouting();

    void ignoreRdsEvent(boolean ignoreRds);

    void registerCallbacks(IFMRadioPlayerServiceCallbacks cb);
    void unregisterCallbacks();
}
