package com.embeddedhttpd

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class EmbeddedHttpdModule internal constructor(val context: ReactApplicationContext) :
  EmbeddedHttpdSpec(context) {

  companion object {
    const val NAME = "EmbeddedHttpd"

    private fun ipToNumber(ip: String): Long =
      ip.split(".").fold(0L) { acc, s -> (acc shl 8) + s.toLong() }

    private fun jsonToObject(json: String): WritableMap {
      lateinit var objToMap: (JSONObject) -> WritableMap
      lateinit var arrToMap: (JSONArray) -> WritableArray

      objToMap = { obj ->
        Arguments.createMap().apply {
          obj.keys().forEach {
            when (val value = obj.get(it)) {
              is Boolean -> putBoolean(it, value)
              is Int -> putInt(it, value)
              is Double -> putDouble(it, value)
              is String -> putString(it, value)
              is JSONObject -> putMap(it, objToMap(value))
              is JSONArray -> putArray(it, arrToMap(value))
              else -> {}
            }
          }
        }
      }

      arrToMap = { arr ->
        Arguments.createArray().apply {
          for (i in 0 until arr.length()) {
            when (val it = arr.get(i)) {
              is Boolean -> pushBoolean(it)
              is Int -> pushInt(it)
              is Double -> pushDouble(it)
              is String -> pushString(it)
              is JSONObject -> pushMap(objToMap(it))
              is JSONArray -> pushArray(arrToMap(it))
              else -> pushNull()
            }
          }
        }
      }

      return JSONObject(json).let { objToMap(it) }
    }
  }

  private val instances = ConcurrentHashMap<Int, EmbeddedServer>()
  private val instancesId = AtomicInteger(0)

  private val requests = ConcurrentHashMap<String, CompletableDeferred<?>>();

  override fun getName(): String = NAME

  protected val coroutineScope: CoroutineScope
    get() = getCurrentActivity()
      ?.getCurrentFocus()
      ?.let { ViewTreeLifecycleOwner.get(it) }
      ?.lifecycleScope
      // [ ] Test concurrency, fallback to GlobalScope on racing conditions
      ?: CoroutineScope(Dispatchers.Default)

  private fun emitEvent(event: String, parameters: WritableMap) {
    context
      // [ ] Enable this again when we know how to check RCT_NEW_ARCH_ENABLED
      // .takeIf { listenerCount > 0 }
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      ?.emit(event, parameters)
  }

  private var listenerCount = 0

  @ReactMethod
  override fun addListener(eventName: String) {
    listenerCount++
  }

  @ReactMethod
  override fun removeListeners(count: Double) {
    listenerCount -= count.toInt()
  }

  @ReactMethod
  override fun createInstance(
    host: String,
    port: Double,
    sslKey: String,
    sslCert: String,
    promise: Promise
  ) {
    coroutineScope.launch {
      val instanceId = instancesId.incrementAndGet()

      runCatching {
        val server = embeddedServer(
          Netty,
          port = port.takeIf { it > 0 }?.toInt() ?: 80,
          host = host.trim().ifEmpty { "0.0.0.0" },
          // [ ] HTTPS requires specifying connector impl, which needs `KeyStore`
          // https://api.ktor.io/ktor-server/ktor-server-core/io.ktor.server.engine/ssl-connector.html
        ) {
          routing {
            get("/*") {
              val requestId = UUID.randomUUID().toString()
              val deferred = CompletableDeferred<ReadableMap>()

              // To be referenced by the `respond()` method
              requests.set(requestId, deferred)

              // Notify JavaScript side about the request
              emitEvent("request", Arguments.createMap().apply {
                putInt("instanceId", instanceId)
                putString("requestId", requestId)
                putMap("request", Arguments.createMap().apply {
                  putString("method", call.request.httpMethod.value)
                  putString("url", call.request.uri)
                  putMap("headers", Arguments.createMap().apply {
                    call.request.headers.forEach { (key, values) ->
                      putString(key, values[0])
                    }
                  })
                  putString("body", call.request.receiveText())
                })
              })

              runCatching {
                val response = withTimeout(60000) { deferred.await() }
                val headers = response
                  .takeIf { it.hasKey("headers") }
                  ?.getMap("headers")
                val contentType = headers
                  ?.getString("Content-Type")
                  ?: "text/plain"
                val statusCode = response
                  .takeIf { it.hasKey("statusCode") }
                  ?.getInt("statusCode")
                  ?: 200

                call.respondText(
                  response.getString("body"),
                  ContentType.parse(contentType),
                  HttpStatusCode.fromValue(statusCode)
                ) {
                  headers?.keys.forEach { key ->
                    headers
                      .getString(key)
                      .takeIf { it.isNotEmpty() }
                      ?.let { header(key, it) }
                  }
                }
              }
              .onFailure {
                promise.reject(
                  it::class.java.simpleName,
                  it.message
                )
              }

              // Finally remove the request reference
              requests.remove(requestId)
            }
          }
        }
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
      .onSuccess {
        instances.set(instanceId, it)
        promise.resolve(instanceId)
      }
    }
  }

  @ReactMethod
  override fun removeInstance(
    instanceId: Double,
    promise: Promise
  ) {
    coroutineScope.launch {
      runCatching {
        instances.remove(instanceId.toInt())
        promise.resolve(null)
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
    }
  }

  @ReactMethod
  override fun start(
    instanceId: Double,
    promise: Promise
  ) {
    coroutineScope.launch {
      runCatching {
        instances.get(instanceId.toInt())?.startSuspend(wait = true)
        promise.resolve(null)
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
    }
  }

  @ReactMethod
  override fun stop(
    instanceId: Double,
    gracePeriodMillis: Double?,
    timeoutMillis: Double?,
    promise: Promise
  ) {
    val argGracePeriod = gracePeriodMillis?.toLong() ?: 60L
    val argTimeout = timeoutMillis?.toLong() ?: 120L

    coroutineScope.launch {
      runCatching {
        instances.get(instanceId.toInt())?.stopSuspend(
          gracePeriodMillis = argGracePeriod,
          timeoutMillis = argTimeout
        )
        promise.resolve(null)
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
    }
  }

  @ReactMethod
  override fun reload(
    instanceId: Double,
    promise: Promise
  ) {
    coroutineScope.launch {
      runCatching {
        instances.get(instanceId.toInt())?.reload()
        promise.resolve(null)
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
    }
  }

  @ReactMethod
  override fun respond(
    instanceId: Double,
    requestId: String,
    status: Double,
    headers: ReadableMap,
    body: String,
    promise: Promise
  ) {
    coroutineScope.launch {
      runCatching {
        val deferred = requests.get(requestId) ?: throw IllegalArgumentException("Request not found")
        val responseMap = Arguments.createMap().apply {
          putInt("statusCode", status.toInt())
          putString("body", body)
          if (headers.hasKey("Content-Type")) {
            putString("Content-Type", headers.getString("Content-Type"))
          }
          if (headers.hasKey("Content-Length")) {
            putInt("Content-Length", headers.getInt("Content-Length"))
          }
          headers.keySet().forEach { key ->
            putString(key, headers.getString(key))
          }
        }
        deferred.complete(responseMap)
        promise.resolve(null)
      }
      .onFailure {
        promise.reject(
          it::class.java.simpleName,
          it.message
        )
      }
    }
  }
}
