@file:OptIn(DelicateCoroutinesApi::class)
package moe.fuqiuluo.shamrock.xposed.actions

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.fuqiuluo.shamrock.remote.service.WebSocketClientService
import moe.fuqiuluo.shamrock.remote.service.WebSocketService
import moe.fuqiuluo.shamrock.remote.service.api.GlobalPusher
import moe.fuqiuluo.shamrock.remote.service.config.ShamrockConfig
import moe.fuqiuluo.shamrock.utils.PlatformUtils
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LogCenter
import mqq.app.MobileQQ
import kotlin.concurrent.timer

internal class InitRemoteService: IAction {
    override fun invoke(ctx: Context) {
        if (!PlatformUtils.isMainProcess()) return

        GlobalScope.launch {
            try {
                moe.fuqiuluo.shamrock.remote.HTTPServer.start(ShamrockConfig.getPort())
            } catch (e: Throwable) {
                LogCenter.log(e.stackTraceToString(), Level.ERROR)
            }
        }

        if (ShamrockConfig.allowWebHook()) {
            GlobalPusher.register(moe.fuqiuluo.shamrock.remote.service.HttpService)
        }

        if (ShamrockConfig.openWebSocket()) {
            startWebSocketServer()
        }

        if (ShamrockConfig.openWebSocketClient()) {
            ShamrockConfig.getWebSocketClientAddress().split(",", "|", "，").forEach { url ->
                if (url.isNotBlank())
                    startWebSocketClient(url)
            }
        }
    }

    private fun startWebSocketServer() {
        GlobalScope.launch {
            try {
                val server = WebSocketService(ShamrockConfig.getWebSocketPort())
                server.start()
            } catch (e: Throwable) {
                LogCenter.log(e.stackTraceToString(), Level.ERROR)
            }
        }
    }

    private fun startWebSocketClient(url: String) {
        GlobalScope.launch {
            try {
                val runtime = MobileQQ.getMobileQQ().waitAppRuntime()
                val curUin = runtime.currentAccountUin
                val wsHeaders = hashMapOf(
                    "X-Client-Role" to "Universal",
                    "X-Self-ID" to curUin
                )
                val token = ShamrockConfig.getToken()
                if (token.isNotBlank()) {
                    wsHeaders["authorization"] = "bearer $token"
                    //wsHeaders["bearer"] = token
                }

                if (url.startsWith("ws://") || url.startsWith("wss://")) {
                    var wsClient = WebSocketClientService(url, wsHeaders)
                    wsClient.connect()
                    timer(initialDelay = 5000L, period = 5000L) {
                        if (wsClient.isClosed || wsClient.isClosing) {
                            GlobalPusher.unregister(wsClient)
                            wsClient = WebSocketClientService(url, wsHeaders)
                            wsClient.connect()
                        }
                    }
                } else {
                    LogCenter.log("被动WebSocket地址不合法: $url", Level.ERROR)
                }
            } catch (e: Throwable) {
                LogCenter.log(e.stackTraceToString(), Level.ERROR)
            }
        }
    }
}