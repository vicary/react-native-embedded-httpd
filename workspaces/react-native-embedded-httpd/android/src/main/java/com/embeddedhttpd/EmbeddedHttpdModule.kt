package com.embeddedhttpd

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import org.json.JSONArray
import org.json.JSONObject

class EmbeddedHttpdModule internal constructor(val context: ReactApplicationContext) :
  EmbeddedHttpdSpec(context) {

  companion object {
    const val NAME = "EmbeddedHttpd"

    private fun mapForEach(
      map: ReadableMap,
      action: (String, String) -> Unit
    ) {
      val it = map.keySetIterator()
      while (it.hasNextKey()) {
        val key = it.nextKey()
        val value = map.getString(key)?.trim()
        if (value.isNullOrEmpty()) continue

        action(key, value)
      }
    }
  }

  private val instances = ConcurrentHashMap<
    Int,
    EmbeddedServer<
      NettyApplicationEngine,
      NettyApplicationEngine.Configuration
    >
  >()

  private val instancesId = AtomicInteger(0)

  protected fun getInstance(instanceId: Double): EmbeddedServer<
    NettyApplicationEngine,
    NettyApplicationEngine.Configuration
  > {
    return instances[instanceId.toInt()]
      ?: throw IllegalArgumentException("Instance $instanceId not found")
  }

  private val requests = ConcurrentHashMap<
    String,
    CompletableDeferred<WritableMap>
  >();

  override fun getName(): String = NAME

  protected val coroutineScope: CoroutineScope
    get() = getCurrentActivity()
      ?.getCurrentFocus()
      ?.findViewTreeLifecycleOwner()
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
        embeddedServer(
          Netty,
          port = port.takeIf { it > 0 }?.toInt() ?: 8080,
          host = host.trim().ifEmpty { "0.0.0.0" },
          // [ ] HTTPS requires specifying connector impl, which needs `KeyStore`
          // https://api.ktor.io/ktor-server/ktor-server-core/io.ktor.server.engine/ssl-connector.html
        ) {
          routing {
            get("/") {
              request(instanceId, call, promise)
            }
            get("/{...}") {
              request(instanceId, call, promise)
            }
            post("/") {
              request(instanceId, call, promise)
            }
            post("/{...}") {
              request(instanceId, call, promise)
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
        getInstance(instanceId).start()
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
        getInstance(instanceId).stopSuspend(
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
        getInstance(instanceId).reload()
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

  protected suspend fun request(
    instanceId: Int,
    call: RoutingCall,
    promise: Promise
  ) {
    val requestId = UUID.randomUUID().toString()
    val deferred = CompletableDeferred<WritableMap>()

    // To be referenced by the `respond()` method
    requests.set(requestId, deferred)

    // Notify JavaScript side about the request
    emitEvent("httpdRequest", Arguments.createMap().apply {
      putInt("instanceId", instanceId)
      putString("requestId", requestId)
      putMap("request", Arguments.createMap().apply {
        putString("method", call.request.local.method.value)
        putString("url", call.request.local.uri)
        putMap("headers", Arguments.createMap().apply {
          call.request.headers.forEach { key, values ->
            putString(key, values[0].toString())
          }
        })
        putString("body", call.receiveText())
      })
    })

    runCatching {
      val response = withTimeout(60000) { deferred.await() }
      val headers = response
        .takeIf { it.hasKey("headers") }
        ?.getMap("headers")
      val contentType = headers
        ?.getString("Content-Type")
        ?.let { ContentType.parse(it)}
        ?: ContentType.Text.Plain
      val statusCode = response
        .takeIf { it.hasKey("statusCode") }
        ?.getInt("statusCode")
        ?.let { HttpStatusCode.fromValue(it) }
        ?: HttpStatusCode.OK

      if (headers != null) {
        mapForEach(headers) { key, value ->
          call.response.headers.append(key, value)
        }
      }

      call.respondText(
        response.getString("body") ?: "",
        contentType,
        statusCode
      )
    }
    .onFailure {
      call.respondText(
        "[${it::class.java.simpleName}] ${it.message}",
        ContentType.Text.Plain,
        HttpStatusCode.InternalServerError
      )

      promise.reject(
        it::class.java.simpleName,
        it.message
      )
    }

    // Finally remove the request reference
    requests.remove(requestId)
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

          val responseHeaders = Arguments.createMap()
          mapForEach(headers) { key, value ->
            responseHeaders.putString(key, value)
          }
          putMap("headers", responseHeaders)
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
