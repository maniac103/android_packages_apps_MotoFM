package com.motorola.fmradio;

interface IFMRadioPlayerService {
    int getServiceStatus();
    boolean powerOn(int freq);
    boolean powerOff();
    boolean tune(int freq);
    boolean seek(int freq, int direction);
    boolean stopSeek();
    boolean scan();
    boolean stopScan();
    boolean setVolume(int volume);
    int getVolume();
    boolean setMute(int mode);
    boolean isMute();
    boolean isPowerOn();
    String getRdsText(int id);
    int getRdsValue(int id);
    boolean setAudioRouting(int mode);
    int getAudioRouting();
    int getAudioMode();
    void setBGMode(boolean background);
    void ignoreRdsEvent(boolean ignoreRds);
}
