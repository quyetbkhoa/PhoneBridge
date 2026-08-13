package com.quyetbkhoa.phonebridge

import android.app.Application
import com.quyetbkhoa.phonebridge.adb.AdbConnection
import com.quyetbkhoa.phonebridge.adb.AdbCrypto
import com.quyetbkhoa.phonebridge.commands.CommandExecutor
import com.quyetbkhoa.phonebridge.data.PhoneBridgeRepository
import com.quyetbkhoa.phonebridge.logging.DeveloperLog
import com.quyetbkhoa.phonebridge.shizuku.ShizukuRemoteStarter
import com.quyetbkhoa.phonebridge.update.AppUpdater
import com.quyetbkhoa.phonebridge.usb.UsbAdbController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PhoneBridgeApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.usbController.start()
    }
}

class AppGraph(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val log = DeveloperLog()
    val repository = PhoneBridgeRepository(application, applicationScope)
    val adbConnection = AdbConnection(AdbCrypto(application), log)
    val commandExecutor = CommandExecutor(adbConnection, repository)
    val shizukuStarter = ShizukuRemoteStarter(commandExecutor)
    val usbController = UsbAdbController(
        application,
        applicationScope,
        adbConnection,
        repository,
        commandExecutor,
        shizukuStarter,
        log
    )
    val updater = AppUpdater(application, applicationScope)
}
