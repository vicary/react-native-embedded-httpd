package com.embeddedhttpd

import com.facebook.react.bridge.ReactApplicationContext

abstract class EmbeddedHttpdSpec internal constructor(context: ReactApplicationContext) :
  NativeEmbeddedHttpdSpec(context) {
}
