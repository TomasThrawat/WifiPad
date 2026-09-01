// AIDL interface exposed by the privileged (shell) process Shizuku spawns.
package com.wifipad.receiver;

interface IGamepadService {
    boolean start(int port);
    void stop();
    boolean isRunning();
    String lastError();
    long packetsReceived();
    void destroy();
}
