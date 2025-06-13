package com.embeddedhttpd

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.NativeModule
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.module.model.ReactModuleInfo
import java.util.HashMap

class EmbeddedHttpdPackage : TurboReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
    if (name == EmbeddedHttpdModule.NAME) {
      EmbeddedHttpdModule(reactContext)
    } else {
      null
    }

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      EmbeddedHttpdModule.NAME to ReactModuleInfo(
        EmbeddedHttpdModule.NAME,
        EmbeddedHttpdModule.NAME,
        false,  // canOverrideExistingModule
        false,  // needsEagerInit
        true,  // hasConstants - deprecated signature required for RN 0.67
        false,  // isCxxModule
        BuildConfig.IS_NEW_ARCHITECTURE_ENABLED  // isTurboModule
      )
    )
  }
}
