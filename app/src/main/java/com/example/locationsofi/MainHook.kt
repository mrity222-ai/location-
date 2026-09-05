package com.example.locationsofi

import android.content.ContentResolver
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // System Framework Hooking (Hide Mock Location Setting)
        if (lpparam.packageName == "android" ||
            lpparam.packageName == "com.android.providers.settings") {
            hookSystemLocation(lpparam)
            return
        }

        // Apply Location Spoofer Hooks across targeted applications
        hookLocationServices(lpparam)
    }

    private fun hookSystemLocation(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Settings.Secure Hooks - Mock Location Setting Hide
            XposedHelpers.findAndHookMethod(
                android.provider.Settings.Secure::class.java.name,
                lpparam.classLoader,
                "getStringForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key == "mock_location") {
                            param.result = "0"  // Mock Location Disabled
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("LocationSpoofer: System hook error - ${t.message}")
        }
    }

    private fun hookLocationServices(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 1. Location.isFromMockProvider() -> false
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "isFromMockProvider",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )

            // 2. Location.isMock() -> false
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "isMock",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )

            // 3. AppOpsManager.checkOp() -> MODE_ALLOWED
            XposedHelpers.findAndHookMethod(
                "android.app.AppOpsManager",
                lpparam.classLoader,
                "checkOp",
                Int::class.java,
                Int::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val op = param.args[0] as? Int ?: return
                        if (op == 10002) {  // OP_MOCK_LOCATION = 10002
                            param.result = 0  // MODE_ALLOWED
                        }
                    }
                }
            )

            // 4. LocationManager.getLastKnownLocation() -> Fake Location
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam.classLoader,
                "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = SpoofManager.getFakeLocation()
                    }
                }
            )

            // 5. LocationListener Callbacks Intercept
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam.classLoader,
                "requestLocationUpdates",
                String::class.java,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                LocationListener::class.java,
                android.os.Looper::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val listener = param.args[3] as? LocationListener
                        if (listener != null) {
                            val fakeLocation = SpoofManager.getFakeLocation()
                            listener.onLocationChanged(fakeLocation)
                        }
                    }
                }
            )

            XposedBridge.log("LocationSpoofer: Hooks applied successfully to ${lpparam.packageName}")

        } catch (t: Throwable) {
            XposedBridge.log("LocationSpoofer: Hook error in ${lpparam.packageName} - ${t.message}")
        }
    }
}
