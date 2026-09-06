package com.example.locationsofi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.DataOutputStream

object RootHelper {

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun performAutoSetup(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        kotlin.concurrent.thread {
            try {
                val pkgName = context.packageName
                val commands = mutableListOf<String>()

                // 1. Auto-grant Mock Location AppOps & Settings
                commands.add("appops set $pkgName MOCK_LOCATION allow")
                commands.add("settings put secure mock_location 1")

                // 2. Clear Google Play Services & Maps Location Cache
                commands.add("pm clear com.google.android.apps.maps")

                // 3. Inject LSPosed Modules DB Configuration
                val dbPaths = arrayOf(
                    "/data/adb/lspd/config/modules.db",
                    "/data/user_de/0/org.lsposed.manager/databases/modules.db"
                )

                val targetScopes = arrayOf(
                    "com.google.android.gms",
                    "com.google.android.gsf",
                    "com.android.location.fused",
                    "com.google.android.apps.maps",
                    "android"
                )

                for (dbPath in dbPaths) {
                    commands.add("if [ -f $dbPath ]; then sqlite3 $dbPath \"INSERT OR REPLACE INTO modules (package_name, enabled) VALUES ('$pkgName', 1);\"; fi")
                    for (scopePkg in targetScopes) {
                        commands.add("if [ -f $dbPath ]; then sqlite3 $dbPath \"INSERT OR REPLACE INTO scope (module_pkg_name, app_pkg_name) VALUES ('$pkgName', '$scopePkg');\"; fi")
                    }
                }

                // Execute Root Command Batch
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                process.waitFor()

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "⚡ 1-Click Root Auto-Setup Applied Successfully!", Toast.LENGTH_LONG).show()
                    onComplete?.invoke(true, "Auto-Setup Completed Successfully! Mock Location & LSPosed Scopes Configured.")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "⚠️ Root Auto-Setup Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(false, "Root Execution Error: ${e.message}")
                }
            }
        }
    }
}
