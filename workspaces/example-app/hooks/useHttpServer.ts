import { useCallback, useEffect, useState } from "react";
import {
  EmbeddedServer,
  FetchCallback,
  serve,
  ServeOptions,
} from "react-native-embedded-httpd";

export const useHttpServer = <Env, Context>(
  fetch: FetchCallback<Env, Context>,
  options?: Omit<ServeOptions<Env, Context>, "fetch">,
) => {
  const fetchCallback = useCallback(fetch, [fetch]);
  const [server, setServer] = useState<EmbeddedServer>();

  useEffect(() => {
    let isMounted = true;

    const server = serve({ ...options, fetch: fetchCallback });

    server.then((s) => {
      if (isMounted) {
        setServer(s);
      }
    });

    return () => {
      isMounted = false;

      // [ ] This implementation may still allows racing condition where the
      // unmounted server instance is in the middle of being disposed. It could
      // be problematic when the exact same host and port is being used again,
      // the new server may fail to start because of port is still in use.
      server.then((s) => s.dispose());
    };
  }, [fetchCallback]);

  return server;
};
