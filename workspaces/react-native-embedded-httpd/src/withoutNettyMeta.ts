import {
  createRunOncePlugin,
  withAppBuildGradle,
  type ConfigPlugin,
} from "@expo/config-plugins";

// @ts-expect-error Output directory is lib/commonjs/ instead of src/
import { name, version } from "../../package.json";

const withoutNettyMeta: ConfigPlugin = (config) => {
  return withAppBuildGradle(config, (mod) => {
    // Exclude Netty to prevent ktor version conflicts
    if (mod.modResults.contents.includes("android {")) {
      mod.modResults.contents = mod.modResults.contents.replace(
        /android\s*{/,
        `android {
    packaging {
      resources {
        excludes += [
          'META-INF/INDEX.LIST',
          'META-INF/io.netty.versions.properties',
        ];
      }
    }`,
      );
    }

    return mod;
  });
};

export default createRunOncePlugin(withoutNettyMeta, name, version);
