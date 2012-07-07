package com.motorola.fmradio;

interface IFMRadioPlayerService {
    boolean powerOn(int freq);
    boolean powerOff();
    boolean isPowerOn();

    boolean tune(int freq);
    boolean seek(int freq, int direction);
    boolean stopSeek();

    boolean scan();
    boolean stopScan();

    boolean setVolume(int volume);
    int getVolume();

    int getAudioMode();

    boolean setAudioRouting(int mode);
    int getAudioRouting();

    void ignoreRdsEvent(boolean ignoreRds);
}
