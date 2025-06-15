import { useHttpServer } from "@/hooks/useHttpServer";
import { useIsHermes } from "@/hooks/useIsHermes";
import { useNewArchitecture } from "@/hooks/useNewArchitecture";
import React, { FunctionComponent } from "react";
import {
  ActivityIndicator,
  Platform,
  SafeAreaView,
  ScrollView,
  Text,
  View,
} from "react-native";

const Home: FunctionComponent = () => {
  const isFabric = useNewArchitecture();
  const isHermes = useIsHermes();
  const server = useHttpServer((request) => {
    return new Response("Hello World");
  });

  return (
    <SafeAreaView className="bg-gray-300">
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        className="min-h-screen"
      >
        <View className="flex h-full gap-3 px-3 py-10">
          <Text className="text-2xl font-bold">Example</Text>

          <View>
            <Text className="text-xl">Environment</Text>
            <View className="flex flex-row gap-3">
              <Text>Fabric: {isFabric ? "✅" : "❎"}</Text>
              <Text>Hermes: {isHermes ? "✅" : "❎"}</Text>
            </View>
          </View>

          {server === undefined ? (
            <View className="flex flex-row items-center gap-1">
              <ActivityIndicator
                size={10}
                color="#9CA3AF"
                className={Platform.OS === "ios" ? "px-2" : undefined}
              />
              <Text className="text-xs text-gray-400">
                Starting HTTP server...
              </Text>
            </View>
          ) : (
            <View className="flex flex-row items-center gap-1">
              <ActivityIndicator
                size={10}
                color="#9CA3AF"
                className={Platform.OS === "ios" ? "px-2" : undefined}
              />
              <Text className="text-xs text-gray-400">
                Listening for requests ...
              </Text>
            </View>
          )}

          <View className="flex flex-row flex-wrap gap-3">
            {/* [ ] Add a button to send HTTP requests it localhost. */}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

export default Home;
