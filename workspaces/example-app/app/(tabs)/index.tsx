import { HttpServerStatus, useHttpServer } from "@/hooks/useHttpServer";
import { useIsHermes } from "@/hooks/useIsHermes";
import { useNewArchitecture } from "@/hooks/useNewArchitecture";
import React, { FunctionComponent } from "react";
import {
  ActivityIndicator,
  Button,
  Platform,
  SafeAreaView,
  ScrollView,
  Text,
  View,
} from "react-native";

const Home: FunctionComponent = () => {
  const isFabric = useNewArchitecture();
  const isHermes = useIsHermes();

  const { status } = useHttpServer(() => {
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

          <Text>Status: {status}</Text>

          {status !== HttpServerStatus.Running ? (
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
            <Button
              onPress={async () => {
                const response = await fetch("http://localhost:8080");
                const text = await response.text();

                console.log("Response text:", { text });
              }}
              // disabled={status !== HttpServerStatus.Running}
              title="Send Request"
            />
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

export default Home;
