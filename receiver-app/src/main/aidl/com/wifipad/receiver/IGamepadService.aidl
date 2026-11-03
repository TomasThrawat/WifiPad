package com.wifipad.receiver;

interface IGamepadService {
    boolean start(int port, String controllerId, String secret);
    void stop();
    boolean isRunning();
    String lastError();
    long packetsReceived();
    long packetsPerSecond();
    long rejectedPackets();
    long lostPackets();
    long lastPacketTimeMs();
    String lastRemoteHost();
    int currentButtons();
    int currentDpad();
    int currentLeftX();
    int currentLeftY();
    int currentRightX();
    int currentRightY();
    int currentLeftTrigger();
    int currentRightTrigger();
    void destroy();
}
