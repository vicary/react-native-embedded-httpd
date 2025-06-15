import type { ConfigContext, ExpoConfig } from "@expo/config";
import { pascalCase } from "change-case";
import { config } from "dotenv";
import * as path from "node:path";
import { name, version } from "./package.json";

config({
  path: [
    path.resolve(__dirname, ".env.local"),
    path.resolve(__dirname, ".env"),
  ],
});

const newArchEnabled = ["1", "true", "yes", "on", "enabled"].includes(
  process.env.RCT_NEW_ARCH_ENABLED?.toLowerCase().trim()!,
);

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: pascalCase(name),
  slug: "example-app",
  version,
  orientation: "portrait",
  icon: "./assets/images/icon.png",
  scheme: "example",
  userInterfaceStyle: "automatic",
  newArchEnabled,
  ios: {
    supportsTablet: true,
    bundleIdentifier: "com.example.app",
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/images/adaptive-icon.png",
      backgroundColor: "#ffffff",
    },
    edgeToEdgeEnabled: true,
    package: "com.example.app",
    permissions: ["android.permission.ACCESS_WIFI_STATE"],
  },
  web: {
    bundler: "metro",
    output: "static",
    favicon: "./assets/images/favicon.png",
  },
  plugins: [
    "expo-font",
    "expo-router",
    "expo-web-browser",
    [
      "expo-splash-screen",
      {
        image: "./assets/images/splash-icon.png",
        imageWidth: 200,
        resizeMode: "contain",
        backgroundColor: "#ffffff",
      },
    ],
    [
      "expo-dev-launcher",
      {
        launchMode: "most-recent",
      },
    ],
    "react-native-embedded-httpd",
  ],
  experiments: {
    turboModules: newArchEnabled,
    typedRoutes: true,
  },
});
