package com.orbit.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.orbit.launcher.core.ui.OrbitTheme
import com.orbit.launcher.feature.home.LauncherFoundationScreen

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgetHost: AppWidgetHost
    private var isDefaultHome by mutableStateOf(false)
    private var homeRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = AppWidgetHost(this, 1024)
        enableEdgeToEdge()
        isDefaultHome = checkDefaultHome()
        setContent {
            val roleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { isDefaultHome = checkDefaultHome() }
            val themeSettings by viewModel.settings.collectAsState()
            androidx.compose.runtime.SideEffect {
                if (themeSettings.wallpaperEnabled) {
                    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.rgb(9, 12, 21)))
                }
            }
            OrbitTheme(themeSettings) {
                var skippedSetup by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(homeRequests) { if (homeRequests > 0) skippedSetup = false }
                if (isDefaultHome || skippedSetup) {
                    LauncherAppScreen(viewModel = viewModel, homeRequests = homeRequests, widgetHost = widgetHost, authenticate = ::authenticate, onBlur = { radius ->
                        if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(radius)
                    })
                } else LauncherFoundationScreen(
                    isDefaultHome = isDefaultHome,
                    homeRequests = homeRequests,
                    onContinue = { skippedSetup = true },
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

    private fun authenticate(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set up biometrics in system settings first", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock hidden apps")
            .setSubtitle("Verify your identity to manage hidden apps and backups")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
        prompt.authenticate(info)
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = checkDefaultHome()
        viewModel.refreshSuggestions()
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
