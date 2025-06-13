import {
  DeviceEventEmitter,
  NativeEventEmitter,
  NativeModules,
  Platform,
} from "react-native";
import type { Spec } from "./NativeEmbeddedHttpd";

// @ts-expect-error ts(7017)
const isTurboModuleEnabled = global.__turboModuleProxy != null;

const NativeInterface: Spec = isTurboModuleEnabled
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  ? require("./NativeEmbeddedHttpd").default
  : NativeModules.EmbeddedHttpd;

if (!NativeInterface) {
  throw new Error(
    "The package 'react-native-embedded-httpd' doesn't seem to be linked. Make sure: \n\n" +
      Platform.select({ ios: "- You have run 'pod install'\n", default: "" }) +
      "- You rebuilt the app after installing the package\n" +
      "- You are not using Expo Go\n",
  );
}

const events = isTurboModuleEnabled
  ? new NativeEventEmitter(NativeInterface)
  : DeviceEventEmitter;

export { events, NativeInterface };
