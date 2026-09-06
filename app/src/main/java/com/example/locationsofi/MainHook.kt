package com.example.locationsofi

import android.content.ContentResolver
import android.location.LocationListener
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
        hookFusedLocationServices(lpparam)
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
        } catch (t: Throwable) {}

        try {
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
        } catch (t: Throwable) {}

        try {
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
        } catch (t: Throwable) {}

        try {
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
        } catch (t: Throwable) {}

        try {
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
        } catch (t: Throwable) {}
    }

    private fun hookFusedLocationServices(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        try {
            // Hook FusedLocationProviderClient.getLastLocation()
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.FusedLocationProviderClient",
                classLoader,
                "getLastLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Return fake location task if invoked
                    }
                }
            )
        } catch (t: Throwable) {}

        try {
            // Hook LocationResult.getLocations() to replace all locations with fake location
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationResult",
                classLoader,
                "getLocations",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fakeLoc = SpoofManager.getFakeLocation()
                        param.result = listOf(fakeLoc)
                    }
                }
            )
        } catch (t: Throwable) {}

        try {
            // Hook LocationResult.getLastLocation()
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationResult",
                classLoader,
                "getLastLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = SpoofManager.getFakeLocation()
                    }
                }
            )
        } catch (t: Throwable) {}

        try {
            // Hook LocationCallback.onLocationResult(LocationResult)
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationCallback",
                classLoader,
                "onLocationResult",
                "com.google.android.gms.location.LocationResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val resultObj = param.args[0] ?: return
                        try {
                            val fakeLoc = SpoofManager.getFakeLocation()
                            XposedHelpers.callMethod(resultObj, "getLastLocation")
                        } catch (e: Throwable) {}
                    }
                }
            )
        } catch (t: Throwable) {}

        XposedBridge.log("LocationSpoofer: Advanced Fused Location hooks applied to ${lpparam.packageName}")
    }
}
