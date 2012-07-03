package com.motorola.android.fmradio;

import com.motorola.android.fmradio.IFMRadioServiceCallback;

interface IFMRadioService {
    boolean tune(int freq);
    boolean getCurrentFreq();
    boolean setAudioMode(int mode);
    boolean getAudioMode();
    boolean setMute(int mode);
    boolean isMute();
    boolean seek(int direction);
    boolean scan();
    boolean stopSeek();
    boolean stopScan();
    boolean setVolume(int volume);
    boolean getVolume();
    int getBand();
    boolean setBand(int band);
    int getMinFrequence();
    int getMaxFrequence();
    int getStepUnit();
    void registerCallback(IFMRadioServiceCallback cb);
    void unregisterCallback(IFMRadioServiceCallback cb);
    boolean setRdsEnable(boolean flag, int mode);
    boolean isRdsEnable();
    boolean getAudioType();
    boolean getRSSI();
    String getRdsPS();
    String getRdsRT();
    String getRdsRTPLUS();
    int getRdsPI();
    int getRdsPTY();
    boolean setRSSI(int rssi);
    String getRDSStationName();
}
