import { useCallback, useEffect, useState } from "react";
import {
  FetchCallback,
  serve,
  ServeOptions,
} from "react-native-embedded-httpd";

export const enum HttpServerStatus {
  Initializing = "initializing",
  Running = "running",
  Stopping = "stopping",
  Stopped = "stopped",
  Error = "error",
}

export const useHttpServer = <Env, Context>(
  fetch: FetchCallback<Env, Context>,
  options?: Omit<ServeOptions<Env, Context>, "fetch">,
) => {
  const fetchCallback = useCallback(fetch, [fetch]);
  const [status, setStatus] = useState<HttpServerStatus>(
    HttpServerStatus.Initializing,
  );

  useEffect(() => {
    const server = serve({ ...options, fetch: fetchCallback }).then((s) => {
      setStatus(HttpServerStatus.Running);

      return s;
    });

    return () => {
      setStatus(HttpServerStatus.Stopping);

      // [ ] This implementation may still allows racing condition where the
      // unmounted server instance is in the middle of being disposed. It could
      // be problematic when the exact same host and port is being used again,
      // the new server may fail to start because of port is still in use.
      server
        .then((s) => s.dispose())
        .finally(() => setStatus(HttpServerStatus.Stopped));
    };
  }, [fetchCallback]);

  return { status };
};
