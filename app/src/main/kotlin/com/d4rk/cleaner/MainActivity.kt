package com.d4rk.cleaner
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.d4rk.cleaner.data.store.DataStore
import com.d4rk.cleaner.databinding.ActivityMainBinding
import com.d4rk.cleaner.notifications.managers.AppUpdateNotificationsManager
import com.d4rk.cleaner.notifications.managers.AppUsageNotificationsManager
import com.d4rk.cleaner.receivers.CleanReceiver
import com.d4rk.cleaner.ui.help.HelpActivity
import com.d4rk.cleaner.ui.imageoptimizer.ImagePickerActivity
import com.d4rk.cleaner.ui.settings.SettingsActivity
import com.d4rk.cleaner.ui.settings.SettingsComposable
import com.d4rk.cleaner.ui.settings.display.theme.AppTheme
import com.d4rk.cleaner.ui.settings.privacy.usage.UsageAndDiagnosticsActivity
import com.d4rk.cleaner.ui.support.SupportActivity
import com.d4rk.cleaner.ui.startup.StartupActivity
import com.d4rk.cleaner.ui.whitelist.WhitelistActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_AUTO
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_UNLABELED
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
class MainActivity : ComponentActivity() {
    private lateinit var dataStore: DataStore
    private lateinit var appUpdateManager: AppUpdateManager
    private var appUpdateNotificationsManager: AppUpdateNotificationsManager = AppUpdateNotificationsManager(this)
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setupUpdateNotifications()
        dataStore = DataStore(this@MainActivity)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize() , color = MaterialTheme.colorScheme.background) {
                    MainComposable(this@MainActivity)
                }
            }
        }
        setupSettings()
    }
    override fun onResume() {
        super.onResume()
        val appUsageNotificationsManager = AppUsageNotificationsManager(this)
        appUsageNotificationsManager.scheduleAppUsageCheck()
        appUpdateNotificationsManager.checkAndSendUpdateNotification()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                @Suppress("DEPRECATION")
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, this, 1)
            }
        }
        startupScreen()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode , resultCode , data)
        if (requestCode == 1) {
            when (resultCode) {
                RESULT_OK -> {
                    val snackbar = Snackbar
                            .make(findViewById(android.R.id.content) , R.string.snack_app_updated , Snackbar.LENGTH_LONG)
                            .setAction(android.R.string.ok , null)
                    snackbar.show()
                }

                RESULT_CANCELED -> {
                    showUpdateFailedSnackbar()
                }

                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    showUpdateFailedSnackbar()
                }
            }
        }
    }

    private fun showUpdateFailedSnackbar() {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.snack_update_failed, Snackbar.LENGTH_LONG)
                .setAction(R.string.try_again) {
                    checkForFlexibleUpdate()
                }
        snackbar.show()
    }

    private fun checkForFlexibleUpdate() {
        lifecycleScope.launch {
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                appUpdateInfo.updateAvailability() != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                @Suppress("DEPRECATION")
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, this@MainActivity, 1)
            }
        }
    }

    private fun setupUpdateNotifications() {
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateNotificationsManager = AppUpdateNotificationsManager(this)
    }

    private fun setupSettings() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(PreferenceManager.getDefaultSharedPreferences(this)?.getString(getString(
            R.string.key_language
        ), getString(R.string.default_value_language))))
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_daily_clean), false)) {
            CleanReceiver.scheduleAlarm(this)
        } else {
            CleanReceiver.cancelAlarm(this)
        }
        lifecycleScope.launch {
           // val language = dataStore.language.first()
            //AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))

            val isEnabled = dataStore.usageAndDiagnostics.first()
            FirebaseAnalytics.getInstance(this@MainActivity).setAnalyticsCollectionEnabled(isEnabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
        }
    }

    private fun startupScreen() {
        lifecycleScope.launch {
            if (dataStore.startup.first()) {
                dataStore.saveStartup(false)
                startActivity(Intent(this@MainActivity, StartupActivity::class.java))
            }
        }
    }
}



/*: AppCompatActivity() {
    // error is here


    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding


    private val handler = Handler(Looper.getMainLooper())
    private val snackbarInterval: Long = 60L * 24 * 60 * 60 * 1000
    private val navController by lazy {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navHostFragment.navController
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize() , color = MaterialTheme.colorScheme.background) {
                    MainComposable()
                }
            }
        }


        setSupportActionBar(binding.toolbar)
        MobileAds.initialize(this)
        binding.adView.loadAd(AdRequest.Builder().build())
        applyAppSettings()
        setupNavigationDrawer()
        setBarsTranslucent(true)
        setupBottomAppBar()

    }


    }
    override fun onSupportNavigateUp(): Boolean {
        return if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        } else {
            navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.close)
            .setMessage(R.string.summary_close)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                @Suppress("DEPRECATION")
                super.onBackPressed()
                moveTaskToBack(true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }
    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_support, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_support -> {
                startActivity(Intent(this, SupportActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun setupNavigationDrawer() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val toggle = ActionBarDrawerToggle(this, drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.isDrawerIndicatorEnabled = true
        toggle.syncState()
        val navView: NavigationView = binding.navView
        appBarConfiguration = AppBarConfiguration(setOf(), drawerLayout)
        navView.setupWithNavController(navController)
        navController.setGraph(R.navigation.mobile_navigation)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0) {
                    setBarsTranslucent(true)
                } else {
                    setBarsTranslucent(false)
                }
            }
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {
                setBarsTranslucent(false)
            }
            override fun onDrawerStateChanged(newState: Int) {}
        })
        navView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isCheckable = false
            when (menuItem.itemId) {
                R.id.nav_share -> {
                    val shareIntent = Intent().apply {
                        this.action = Intent.ACTION_SEND
                        this.putExtra(Intent.EXTRA_TEXT, getString(R.string.summary_share_message, "https://play.google.com/store/apps/details?id=${packageName}"))
                        this.type = "text/plain"
                    }
                    startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_email_using)))
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_updates -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/D4rK7355608/${packageName}/blob/master/CHANGELOG.md")))
                }
                R.id.nav_image_optimizer -> {
                    startActivity(Intent(this, ImagePickerActivity::class.java))
                }
                R.id.nav_help -> {
                    startActivity(Intent(this, HelpActivity::class.java))
                }
                R.id.nav_whitelist -> {
                    startActivity(Intent(this, WhitelistActivity::class.java))
                }
                else -> {
                    navController.navigate(menuItem.itemId)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }
    }
    @Suppress("DEPRECATION")
    private fun setBarsTranslucent(translucent: Boolean) {
        if (!translucent) {
            return
        }

        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }
    }
    private fun setupBottomAppBar() {
        val bottomNavigationBarLabelsValues = resources.getStringArray(R.array.preference_bottom_navigation_bar_labels_values)
        binding.bottomNavView.labelVisibilityMode = when (PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_bottom_navigation_bar_labels), getString(R.string.default_value_bottom_navigation_bar_labels))) {
            bottomNavigationBarLabelsValues[0] -> LABEL_VISIBILITY_LABELED
            bottomNavigationBarLabelsValues[1] -> LABEL_VISIBILITY_SELECTED
            bottomNavigationBarLabelsValues[2] -> LABEL_VISIBILITY_UNLABELED
            else -> LABEL_VISIBILITY_AUTO
        }
        val defaultTabValues = resources.getStringArray(R.array.preference_default_tab_values)
        val startFragmentId = when (PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_default_tab), getString(R.string.default_value_tab))) {
            defaultTabValues[0] -> R.id.navigation_home
            defaultTabValues[1] -> R.id.navigation_app_manager
            defaultTabValues[2] -> R.id.navigation_memory_manager
            else -> R.id.navigation_home
        }
        navController.graph.setStartDestination(startFragmentId)
        navController.navigate(startFragmentId)
        binding.bottomNavView.setupWithNavController(navController)
    }
    private fun applyAppSettings() {
        val themeValues = resources.getStringArray(R.array.preference_theme_values)
        when (PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_theme), getString(
            R.string.default_value_theme
        ))) {
            themeValues[0] -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            themeValues[1] -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            themeValues[2] -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            themeValues[3] -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
        }
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_amoled_mode), false)) {
                binding.root.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
                binding.bottomNavView.itemBackground = ColorDrawable(ContextCompat.getColor(this, android.R.color.black))
            }
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(PreferenceManager.getDefaultSharedPreferences(this)?.getString(getString(
            R.string.key_language
        ), getString(R.string.default_value_language))))
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_daily_clean), false)) {
            CleanReceiver.scheduleAlarm(this)
        } else {
            CleanReceiver.cancelAlarm(this)
        }


        lifecycleScope.launch {
            val isEnabled = dataStore.usageAndDiagnostics.first()
            FirebaseAnalytics.getInstance(this@MainActivity).setAnalyticsCollectionEnabled(isEnabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
        }

        // Old Firebase method (shared preference)
*//*        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_firebase_analytics), true).also { isEnabled ->
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isEnabled)
        }
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_firebase_crashlytics), true).also { isEnabled ->
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
        }*//*
    }
    private fun showSnackbar() {
        Snackbar.make(binding.root, getString(R.string.snack_support), Snackbar.LENGTH_LONG).setAction(getString(android.R.string.ok)) {
            val intent = Intent(this, SupportActivity::class.java)
            startActivity(intent)
        }
            .show()
        job = scope.launch {
            delay(snackbarInterval)
            showSnackbar()
        }
    }

    private fun showUpdateFailedSnackbar() {
        val snackbar = Snackbar.make(binding.root, R.string.snack_update_failed, Snackbar.LENGTH_LONG)
            .setAction(R.string.try_again) {
                checkForFlexibleUpdate()
            }
        snackbar.show()
    }
    private fun checkForFlexibleUpdate() {
        job = scope.launch {
            try {
                val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    appUpdateInfo.updateAvailability() != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    @Suppress("DEPRECATION")
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, this@MainActivity, requestUpdateCode)
                }
            } catch (_: Exception) { }
        }
    }
}*/