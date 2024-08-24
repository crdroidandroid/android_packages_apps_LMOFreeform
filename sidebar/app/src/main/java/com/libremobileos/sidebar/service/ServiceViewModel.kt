package com.libremobileos.sidebar.service

import android.app.Application
import android.app.prediction.AppPredictionContext
import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerExecutor
import android.os.UserHandle
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.bean.AppInfo
import com.libremobileos.sidebar.room.DatabaseRepository
import com.libremobileos.sidebar.systemapi.UserHandleHidden
import com.libremobileos.sidebar.utils.Logger
import com.libremobileos.sidebar.utils.contains
import com.libremobileos.sidebar.utils.getInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * @author KindBrave
 * @since 2023/8/25
 */
class ServiceViewModel(private val application: Application): AndroidViewModel(application) {
    private val logger = Logger("ServiceViewModel")

    private val repository = DatabaseRepository(application)
    private val sp = application.applicationContext.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)

    val sidebarAppListFlow: StateFlow<List<AppInfo>>
        get() = _sidebarAppList.asStateFlow()
    private val _sidebarAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    private val predictedAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    private val launcherApps = application.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val appPredictionManager = application.getSystemService(AppPredictionManager::class.java)

    private lateinit var appPredictor: AppPredictor
    private val handlerExecutor = HandlerExecutor(Handler())

    val allAppActivity = AppInfo(
        "",
        AppCompatResources.getDrawable(application.applicationContext, R.drawable.ic_all)!!,
        ALL_APP_PACKAGE,
        ALL_APP_ACTIVITY,
        0
    )

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            logger.d("onPackageRemoved: $packageName")
            _sidebarAppList.value.getInfo(packageName, user)?.let {
                repository.deleteSidebarApp(it.packageName, it.activityName, it.userId)
            }
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {

        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            logger.d("onPackageChanged: $packageName")
            val appInfo = application.packageManager.getApplicationInfo(packageName, 0)
            if (!appInfo.enabled) {
                onPackageRemoved(packageName, user)
            }
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {

        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) {

        }
    }

    private val appPredictionCallback = object : AppPredictor.Callback {
        override fun onTargetsAvailable(targets: List<AppTarget>) {
            logger.d("appPredictionCallback targets: $targets")
            predictedAppList.value = targets
                .take(MAX_PREDICTED_APPS)
                .mapNotNull { target ->
                    runCatching {
                        val info = application.packageManager.getApplicationInfo(target.packageName, PackageManager.GET_ACTIVITIES)
                        val launchIntent = application.packageManager.getLaunchIntentForPackage(target.packageName)
                        val userId = UserHandleHidden.getUserId(target.user)
                        AppInfo(
                            info.loadLabel(application.packageManager).toString(),
                            info.loadIcon(application.packageManager),
                            info.packageName,
                            launchIntent!!.component!!.className,
                            userId
                        )
                    }.onFailure { e ->
                        logger.e("failed to add $target: ", e)
                    }.getOrNull()
                }
        }
    }

    companion object {
        private const val ALL_APP_PACKAGE = "com.libremobileos.sidebar"
        private const val ALL_APP_ACTIVITY = "com.libremobileos.sidebar.ui.all_app.AllAppActivity"
        private const val MAX_PREDICTED_APPS = 6
    }

    init {
        logger.d("init")
        initSidebarAppList()
        launcherApps.registerCallback(launcherAppsCallback)
        registerAppPredictionCallback()
    }

    override fun onCleared() {
        logger.d("onCleared")
        launcherApps.unregisterCallback(launcherAppsCallback)
        appPredictor.unregisterPredictionUpdates(appPredictionCallback)
    }

    private fun initSidebarAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAllSidebarAppsByFlow()
                .combine(predictedAppList) { sidebarApps, predictedApps ->
                    mutableListOf<AppInfo>().apply {
                        logger.d("sidebarApps=$sidebarApps predictedApps=$predictedApps")
                        sidebarApps?.forEach { entity ->
                            runCatching {
                                val info = application.packageManager.getApplicationInfo(entity.packageName, PackageManager.GET_ACTIVITIES)
                                if (!info.enabled) {
                                    throw Exception("package is disabled.")
                                }
                                add(
                                    AppInfo(
                                        "${info.loadLabel(application.packageManager)}",
                                        info.loadIcon(application.packageManager),
                                        entity.packageName,
                                        entity.activityName,
                                        entity.userId
                                    )
                                )
                            }.onFailure { e ->
                                logger.e("initSidebarAppList: failed to add $entity: ", e)
                                repository.deleteSidebarApp(entity.packageName, entity.activityName, entity.userId)
                            }
                        }
                        addAll(
                            predictedApps.filter { sidebarApps?.contains(it)?.not() ?: true }
                        )
                    }
                }
                .collect { sidebarAppList ->
                    logger.d("initSidebarAppList: combinedList=$sidebarAppList")
                    _sidebarAppList.value = sidebarAppList.toList()
                }
        }
    }

    private fun registerAppPredictionCallback() {
        appPredictor = appPredictionManager.createAppPredictionSession(
            AppPredictionContext.Builder(application.applicationContext)
                .setUiSurface("hotseat")
                .setPredictedTargetCount(MAX_PREDICTED_APPS)
                .build()
        )
        appPredictor.registerPredictionUpdates(handlerExecutor, appPredictionCallback)
        appPredictor.requestPredictionUpdate()
    }

    fun getIntSp(name: String, defaultValue: Int): Int {
        return sp.getInt(name, defaultValue)
    }

    fun getBooleanSp(name: String, defaultValue: Boolean): Boolean {
        return sp.getBoolean(name, defaultValue)
    }

    fun setIntSp(name: String, value: Int) {
        sp.edit().apply {
            putInt(name, value)
            apply()
        }
    }

    fun registerSpChangeListener(listener: OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterSpChangeListener(listener: OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
