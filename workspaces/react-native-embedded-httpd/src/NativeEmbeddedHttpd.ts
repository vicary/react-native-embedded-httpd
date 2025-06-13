import { type TurboModule, TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {
  addListener(eventType: string): void;

  removeListeners(count: number): void;

  createInstance(
    host: string,
    port: number,
    sslKey: string,
    sslCert: string,
  ): Promise<number>;

  /**
   * Remove the native reference to the instance to preserve memory.
   */
  removeInstance(instanceId: number): Promise<void>;

  start(instanceId: number): Promise<void>;

  stop(
    instanceId: number,
    gracePeriodMillis?: number,
    timeoutMillis?: number,
  ): Promise<void>;

  reload(instanceId: number): Promise<void>;

  respond(
    instanceId: number,
    requestId: string,
    status: number,
    headers: Object,
    body: string,
  ): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>("EmbeddedHttpd");
