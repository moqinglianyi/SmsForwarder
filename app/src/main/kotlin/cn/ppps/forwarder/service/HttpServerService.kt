package cn.ppps.forwarder.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import cn.ppps.forwarder.App
import cn.ppps.forwarder.utils.HTTP_SERVER_TIME_OUT
import cn.ppps.forwarder.utils.HttpServerUtils
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SettingUtils
import com.yanzhenjie.andserver.AndServer
import com.yanzhenjie.andserver.Server
import java.util.concurrent.TimeUnit

@Suppress("PrivatePropertyName")
class HttpServerService : Service(), Server.ServerListener {

    private val TAG: String = HttpServerService::class.java.simpleName
    private var serverStarted = false
    private val server by lazy {
        AndServer.webServer(this).port(HttpServerUtils.serverPort).listener(this).timeout(HTTP_SERVER_TIME_OUT, TimeUnit.SECONDS).build()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // 本版本仅保留 Gotify 转发，HTTP Server 及其远程 API 全部关闭。
        if (!App.HTTP_SERVER_FEATURE_ENABLED || SettingUtils.enablePureClientMode || !HttpServerUtils.hasValidSecurityConfig()) {
            Log.w(TAG, "HTTP Server 未启动：纯客户端模式或未配置安全鉴权")
            stopSelf()
            return
        }

        Log.i(TAG, "HTTP Server starting on port ${HttpServerUtils.serverPort}")
        server.startup()
        serverStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        //纯客户端模式
        if (SettingUtils.enablePureClientMode) return

        Log.i(TAG, "onDestroy: ")
        if (serverStarted) server.shutdown()
    }

    override fun onException(e: Exception?) {
        Log.i(TAG, "onException: ")
    }

    override fun onStarted() {
        Log.i(TAG, "onStarted: ")
    }

    override fun onStopped() {
        Log.i(TAG, "onStopped: ")
    }
}
