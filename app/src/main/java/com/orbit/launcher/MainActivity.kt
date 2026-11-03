package com.orbit.launcher

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orbit.launcher.core.ui.OrbitTheme
import com.orbit.launcher.feature.home.LauncherFoundationScreen

class MainActivity : ComponentActivity() {
    private var isDefaultHome by mutableStateOf(false)
    private var homeRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isDefaultHome = checkDefaultHome()
        setContent {
            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { isDefaultHome = checkDefaultHome() }
            OrbitTheme {
                LauncherFoundationScreen(
                    isDefaultHome = isDefaultHome,
                    homeRequests = homeRequests,
                    onChooseDefault = {
                        try {
                            val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                getSystemService(RoleManager::class.java)
                            } else {
                                null
                            }
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                            ) {
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                            } else {
                                Intent(Settings.ACTION_HOME_SETTINGS)
                            }
                            roleLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(this, R.string.home_settings_unavailable, Toast.LENGTH_LONG).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(this, R.string.home_settings_unavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = checkDefaultHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeRequests++
            isDefaultHome = checkDefaultHome()
        }
    }

    private fun checkDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }
}
