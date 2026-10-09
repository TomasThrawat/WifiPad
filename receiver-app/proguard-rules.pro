# Keep the class Shizuku starts in its shell-side user-service process.
-keep class com.wifipad.receiver.GamepadUserService { *; }
# AIDL's Binder interface is shared across the phone process and the Shizuku process.
-keep class com.wifipad.receiver.IGamepadService { *; }
-keep class com.wifipad.receiver.IGamepadService$Stub { *; }
-keep class com.wifipad.receiver.IGamepadService$Stub$Proxy { *; }
