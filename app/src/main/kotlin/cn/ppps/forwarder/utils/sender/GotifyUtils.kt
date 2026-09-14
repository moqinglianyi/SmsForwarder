package cn.ppps.forwarder.utils.sender

import cn.ppps.forwarder.database.entity.Rule
import cn.ppps.forwarder.entity.MsgInfo
import cn.ppps.forwarder.entity.result.GotifyResult
import cn.ppps.forwarder.entity.setting.GotifySetting
import cn.ppps.forwarder.utils.Log
import cn.ppps.forwarder.utils.SendUtils
import cn.ppps.forwarder.utils.SettingUtils
import cn.ppps.forwarder.utils.interceptor.BasicAuthInterceptor
import cn.ppps.forwarder.utils.interceptor.LoggingInterceptor
import com.google.gson.Gson
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.callback.SimpleCallBack
import com.xuexiang.xhttp2.exception.ApiException
import java.net.URI

class GotifyUtils {
    companion object {

        private val TAG: String = GotifyUtils::class.java.simpleName

        fun sendMsg(
            setting: GotifySetting,
            msgInfo: MsgInfo,
            rule: Rule? = null,
            senderIndex: Int = 0,
            logId: Long = 0L,
            msgId: Long = 0L
        ) {
            val title: String = if (rule != null) {
                msgInfo.getTitleForSend(setting.title, rule.regexReplace, rule.title)
            } else {
                msgInfo.getTitleForSend(setting.title)
            }
            val content: String = if (rule != null) {
                msgInfo.getContentForSend(rule.smsTemplate, rule.regexReplace, rule.title)
            } else {
                msgInfo.getContentForSend(SettingUtils.smsTemplate)
            }

            val requestUrl = try {
                normalizeEndpoint(setting.webServer)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Gotify 地址无效: ${e.message}")
                SendUtils.updateLogs(logId, 0, "Gotify 地址无效")
                SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
                return
            }

            // 支持 URL 中的 HTTP Basic Authentication，但绝不把凭据写入日志。
            val match = Regex("^(https://)([^:]+):([^@]+)@(.+)$", RegexOption.IGNORE_CASE).find(requestUrl)
            val request = if (match != null) {
                XHttp.post(match.groupValues[1] + match.groupValues[4])
                    .addInterceptor(BasicAuthInterceptor(match.groupValues[2], match.groupValues[3]))
            } else {
                XHttp.post(requestUrl)
            }

            request.params("title", title)
                .params("message", content)
                .params("priority", setting.priority)
                .keepJson(true)
                .retryCount(SettingUtils.requestRetryTimes)
                .retryDelay(SettingUtils.requestDelayTime * 1000)
                .retryIncreaseDelay(SettingUtils.requestDelayTime * 1000)
                .timeStamp(true)
                .addInterceptor(LoggingInterceptor(logId))
                .execute(object : SimpleCallBack<String>() {

                    override fun onError(e: ApiException) {
                        Log.e(TAG, "Gotify 请求失败: ${e.displayMessage}")
                        val status = 0
                        SendUtils.updateLogs(logId, status, e.displayMessage)
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }

                    override fun onSuccess(response: String) {
                        val resp = Gson().fromJson(response, GotifyResult::class.java)
                        val status = if (resp?.id != null) 2 else 0
                        // 日志只保留结果，不记录 Gotify 返回体。
                        SendUtils.updateLogs(logId, status, if (status == 2) "success" else "Gotify 返回异常")
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }

                })

        }

        private fun normalizeEndpoint(raw: String): String {
            val value = raw.trim()
            require(value.isNotEmpty()) { "地址为空" }
            val uri = URI(value)
            require(uri.scheme.equals("https", ignoreCase = true)) { "仅允许 HTTPS" }
            require(uri.host != null) { "主机名无效" }
            // Gotify Web UI 常见地址为 https://host/#/；发送接口应为 /message。
            val apiPath = if (uri.path.isNullOrBlank() || uri.path == "/") "/message" else uri.path
            return URI("https", uri.userInfo, uri.host, uri.port, apiPath, uri.query, null).toString()
        }

    }
}
