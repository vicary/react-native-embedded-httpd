package com.embeddedhttpd

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReadableMap

abstract class EmbeddedHttpdSpec internal constructor(context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
  abstract fun addListener(eventName: String)
  abstract fun removeListeners(count: Double)
  abstract fun createInstance(instanceId: Double, port: Double, sslKey: String, sslCert: String, promise: Promise)
  abstract fun removeInstance(instanceId: Double, promise: Promise)
  abstract fun start(instanceId: Double, promise: Promise)
  abstract fun stop(instanceId: Double, gracePeriodMillis: Double, timeoutMillis: Double, promise: Promise)
  abstract fun reload(instanceId: Double, promise: Promise)
  abstract fun respond(instanceId: Double, requestId: String, status: Double, headers: ReadableMap, body: String, promise: Promise)
}
