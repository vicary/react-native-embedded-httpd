import {
  createRunOncePlugin,
  withAppBuildGradle,
  type ConfigPlugin,
} from "@expo/config-plugins";

// @ts-expect-error Output directory is lib/commonjs/ instead of src/
import { name, version } from "../../package.json";

const withoutNettyMeta: ConfigPlugin = (config) => {
  return withAppBuildGradle(config, (mod) => {
    const excludeList = [
      "META-INF/INDEX.LIST",
      "META-INF/io.netty.versions.properties",
    ];

    // Exclude Netty to prevent ktor version conflicts
    if (mod.modResults.contents.includes("packagingOptions {")) {
      mod.modResults.contents = mod.modResults.contents.replace(
        /packagingOptions\s*{/,
        `packagingOptions {
        ${excludeList.map((item) => `pickFirst '${item}'`).join("\n")}
    }`,
      );
    } else if (mod.modResults.contents.includes("android {")) {
      mod.modResults.contents = mod.modResults.contents.replace(
        /android\s*{/,
        `android {
    packaging {
        resources {
            excludes += [${excludeList.map((item) => `'${item}'`).join(", ")}];
        }
    }`,
      );
    } else {
      mod.modResults.contents += `
android {
    packaging {
        resources {
            excludes += [${excludeList.map((item) => `'${item}'`).join(", ")}];
        }
    }
}`;
    }

    return mod;
  });
};

export default createRunOncePlugin(withoutNettyMeta, name, version);
