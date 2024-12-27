package com.libremobileos.sidebar.ui.sidebar

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.bean.SidebarAppInfo
import com.libremobileos.sidebar.room.DatabaseRepository
import com.libremobileos.sidebar.service.SidebarService
import com.libremobileos.sidebar.utils.Logger
import com.libremobileos.sidebar.utils.contains
import com.libremobileos.sidebar.utils.isResizeableActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Collections

/**
 * @author KindBrave
 * @since 2023/10/21
 */
class SidebarSettingsViewModel(private val application: Application) : AndroidViewModel(application) {
    private val logger = Logger("SidebarSettingsViewModel")
    private val repository = DatabaseRepository(application)
    private val allAppList = ArrayList<SidebarAppInfo>()
    val appListFlow: StateFlow<List<SidebarAppInfo>>
        get() = _appList.asStateFlow()
    private val _appList = MutableStateFlow<List<SidebarAppInfo>>(emptyList())
    private val appComparator = AppComparator()

    private val launcherApps = application.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = application.getSystemService(Context.USER_SERVICE) as UserManager
    private val sp = application.applicationContext.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)

    init {
        logger.d("init")
        initAllAppList()
    }

    override fun onCleared() {
        logger.d("onCleared")
    }

    fun getSidebarEnabled(): Boolean =
        sp.getBoolean(SidebarService.SIDELINE, false)

    fun setSidebarEnabled(enabled: Boolean) =
        sp.edit()
            .putBoolean(SidebarService.SIDELINE, enabled)
            .apply()

    fun addSidebarApp(appInfo: SidebarAppInfo) {
        repository.insertSidebarApp(appInfo.packageName, appInfo.activityName, appInfo.userId)
    }

    fun deleteSidebarApp(appInfo: SidebarAppInfo) {
        repository.deleteSidebarApp(appInfo.packageName, appInfo.activityName, appInfo.userId)
    }

    private fun initAllAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            val sidebarAppList = repository.getAllSidebarWithoutLiveData()
            userManager.userProfiles.forEach { userHandle ->
                val list = launcherApps.getActivityList(null, userHandle)
                list.forEach { info ->
                    val component = info.componentName
                    if (!application.isResizeableActivity(component)) {
                        logger.d("activity not resizeable, skipped $component")
                    } else {
                        val userId = userHandle.identifier
                        allAppList.add(
                            SidebarAppInfo(
                                "${info.label}${if (userId != 0) -userId else ""}",
                                info.applicationInfo.loadIcon(application.packageManager),
                                component.packageName,
                                component.className,
                                userId,
                                sidebarAppList?.contains(info.componentName.packageName, info.componentName.className, userId) ?: false
                            )
                        )
                    }
                }
            }
            Collections.sort(allAppList, appComparator)
            _appList.value = allAppList
            logger.d("emitted allAppList: $allAppList")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SidebarSettingsViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                )
            }
        }
    }

    private inner class AppComparator : Comparator<SidebarAppInfo> {
        override fun compare(p0: SidebarAppInfo, p1: SidebarAppInfo): Int {
            return when {
                // put checked items first
                p0.isSidebarApp && !p1.isSidebarApp -> -1
                p1.isSidebarApp && !p0.isSidebarApp -> 1
                else -> Collator.getInstance().compare(p0.label, p1.label)
            }
        }
    }
}
