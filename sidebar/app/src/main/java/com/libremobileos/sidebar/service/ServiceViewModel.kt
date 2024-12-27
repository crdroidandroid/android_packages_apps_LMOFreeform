package com.libremobileos.sidebar.service

import android.app.Application
import android.app.prediction.AppPredictionContext
import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.content.Context
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
import com.libremobileos.sidebar.utils.Logger
import com.libremobileos.sidebar.utils.contains
import com.libremobileos.sidebar.utils.getInfo
import com.libremobileos.sidebar.utils.isResizeableActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
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

    val sidebarAppListFlow: StateFlow<List<AppInfo>>
        get() = _sidebarAppList.asStateFlow()
    private val _sidebarAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    private val predictedAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    private val launcherApps = application.getSystemService(Context.LAUNCHER_APPS_SERVICE)!! as LauncherApps
    private val appPredictionManager = application.getSystemService(AppPredictionManager::class.java)

    private var appPredictor: AppPredictor? = null
    private val handlerExecutor = HandlerExecutor(Handler())
    private var callbacksRegistered = false

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
                        val component = launchIntent!!.component!!
                        val userId = target.user.identifier
                        if (!application.isResizeableActivity(component)) {
                            logger.d("appPredictionCallback: activity is not resizeable, skipped $target")
                            null
                        } else {
                            AppInfo(
                                info.loadLabel(application.packageManager).toString(),
                                info.loadIcon(application.packageManager),
                                info.packageName,
                                component.className,
                                userId
                            )
                        }
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
    }

    fun registerCallbacks() {
        if (callbacksRegistered) return
        logger.d("registerCallbacks")
        initSidebarAppList()
        launcherApps.registerCallback(launcherAppsCallback)
        registerAppPredictionCallback()
        callbacksRegistered = true
    }

    fun unregisterCallbacks() {
        if (!callbacksRegistered) return
        logger.d("unregisterCallbacks")
        launcherApps.unregisterCallback(launcherAppsCallback)
        appPredictor?.unregisterPredictionUpdates(appPredictionCallback)
        viewModelScope.coroutineContext.cancelChildren()
        callbacksRegistered = false
    }

    fun destroy() {
        logger.d("destroy")
        runCatching { viewModelScope.cancel() }
    }

    private fun registerAppPredictionCallback() {
        if (appPredictionManager == null) {
            logger.e("appPredictionManager is null!")
            return
        }
        appPredictor = appPredictionManager.createAppPredictionSession(
            AppPredictionContext.Builder(application.applicationContext)
                .setUiSurface("hotseat")
                .setPredictedTargetCount(MAX_PREDICTED_APPS)
                .build()
        ).apply {
            registerPredictionUpdates(handlerExecutor, appPredictionCallback)
            requestPredictionUpdate()
        }
    }

    private fun initSidebarAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAllSidebarAppsByFlow()
                .combine(predictedAppList) { sidebarApps, predictedApps ->
                    mutableListOf<AppInfo>().apply {
                        logger.d("sidebarApps=$sidebarApps predictedApps=$predictedApps")
                        sidebarApps?.forEach { entity ->
                            runCatching {
                                if (!application.isResizeableActivity(entity.packageName, entity.activityName)) {
                                    throw Exception("activity is not resizeable")
                                }
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
                                logger.w("initSidebarAppList: removing $entity: ", e)
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
}

