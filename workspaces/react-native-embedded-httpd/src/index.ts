import { events, NativeInterface } from "./NativeInterface";

export type FileContent = string | Blob | ArrayBuffer | Uint8Array;

async function encodeFileContent(content: FileContent): Promise<string> {
  if (typeof content === "string") {
    return content;
  } else {
    if (content instanceof Blob) {
      content = await content.bytes();
    }

    return new TextDecoder().decode(content);
  }
}

export type FetchCallback<Env, Context> = (
  request: Request,
  env: Env,
  context: Context,
) => Promise<Response> | Response;

export type ServeOptions<Env = never, Context = never> = {
  fetch: FetchCallback<Env, Context>;

  /**
   * @default 80
   */
  port?: number;

  /**
   * @default "0.0.0.0"
   */
  hostname?: string;

  ssl?: {
    key: string | Blob | ArrayBuffer | Uint8Array;
    cert: string | Blob | ArrayBuffer | Uint8Array;
  };
};

export type StopOptions = {
  gracePeriodMillis?: number;
  timeoutMillis?: number;
};

/**
 * Method exposed from Ktor EmbeddedServer
 *
 * @see https://api.ktor.io/ktor-server/ktor-server-core/io.ktor.server.engine/-embedded-server/index.html
 */
class EmbeddedServer implements AsyncDisposable {
  constructor(private readonly instanceId: number) {}

  #active = false;

  #mutex = Promise.resolve();

  async [Symbol.asyncDispose]() {
    await this.dispose();
  }

  /**
   * Gracefully stops the server and remove both instance reference from both
   * native and JavaScript side.
   */
  async dispose() {
    await this.stop();
    await NativeInterface.removeInstance(this.instanceId);

    servers.delete(this.instanceId);

    return this;
  }

  async reload() {
    await NativeInterface.reload(this.instanceId);

    return this;
  }

  /**
   * Use `startSuspend(wait: true)` under the hood.
   */
  async start() {
    this.#mutex = this.#mutex.then(async () => {
      if (this.#active) {
        return;
      }

      this.#active = true;

      await NativeInterface.start(this.instanceId);
    });

    await this.#mutex;
  }

  /**
   * Use `stopSuspend(options)` under the hood.
   */
  async stop(options?: StopOptions) {
    this.#mutex = this.#mutex.then(async () => {
      if (!this.#active) {
        return;
      }

      this.#active = false;

      await NativeInterface.stop(
        this.instanceId,
        options?.gracePeriodMillis ?? -1,
        options?.timeoutMillis ?? -1,
      );
    });

    await this.#mutex;
  }
}

const servers = new Map<
  number,
  {
    server: EmbeddedServer;
    fetch: FetchCallback<unknown, unknown>;
  }
>();

type NativeRequestEvent = {
  instanceId: number;
  requestId: string;
  request: NativeReqeustLike;
};

type NativeReqeustLike = {
  method: string;
  url: string;
  headers: HeadersInit;
  body?: string;
};

function fromNativeRequest({
  headers,
  method,
  url,
  body,
}: NativeReqeustLike): Request {
  headers = new Headers(headers);

  switch (method.toUpperCase()) {
    case "GET":
    case "HEAD":
      return new Request(url, {
        method,
        headers: new Headers(headers),
      });
    default:
      return new Request(url, {
        method,
        headers: new Headers(headers),
        body: new Blob([body ?? ""]),
      });
  }
}

type NativeResponseLike = {
  status: number;
  headers: Record<string, string>;
  body?: string;
};

async function toNativeResponse(
  response: Response,
): Promise<NativeResponseLike> {
  const responseObject: NativeResponseLike = {
    status: response.status,
    headers: {},
  };

  for (const [key, value] of response.headers) {
    // Assumption: `Response` already normalized header names via `Header` impl
    responseObject.headers[key] = value;
  }

  if (response.body) {
    responseObject.body = await streamToString(response.body);
  }

  return responseObject;

  async function* genChunks<R>(stream: ReadableStream<R>) {
    const reader = stream.getReader();

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        return;
      }

      yield value;
    }
  }

  /**
   * Converts the contents of a ReadableStream to a string.
   */
  async function streamToString(
    stream: ReadableStream<Uint8Array>,
    encoding?: string,
  ) {
    const decoder = new TextDecoder(encoding);
    let result = "";

    for await (const chunk of genChunks(stream)) {
      result += decoder.decode(chunk, { stream: true });
    }

    return result;
  }

  /**
   * Converts the contents of a ReadableStream to a Base64-encoded string.
  function combineChunks(chunks: Uint8Array[]): Uint8Array {
    const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const result = new Uint8Array(totalLength);
    let offset = 0;
    for (const chunk of chunks) {
      result.set(chunk, offset);
      offset += chunk.length;
    }

    return result;
  }

  async function bodyToBinary(stream: ReadableStream<Uint8Array>) {
    const chunks: Uint8Array[] = [];
    for await (const chunk of genChunks(stream)) {
      chunks.push(chunk);
    }

    const buffer = combineChunks(chunks);
    return btoa(String.fromCharCode(...buffer));
  }
   */
}

events.addListener("httpRequest", async (event: NativeRequestEvent) => {
  const server = servers.get(event.instanceId);
  if (!server) {
    console.warn(
      `Server instance ${event.instanceId} not found for incoming request ${event.requestId}.`,
    );

    // Simply drop the native reference
    await NativeInterface.removeInstance(event.instanceId);

    return;
  }

  const request = fromNativeRequest(event.request);
  const response = await server.fetch(request, undefined, undefined);
  const { status, headers, body } = await toNativeResponse(response);

  await NativeInterface.respond(
    event.instanceId,
    event.requestId,
    status,
    headers,
    body ?? "",
  );
});

export async function serve<Env = never, Context = never>({
  fetch,
  port = 80,
  hostname = "0.0.0.0",
  ssl,
}: ServeOptions<Env, Context>): Promise<EmbeddedServer> {
  const sslKey = ssl?.key ? await encodeFileContent(ssl.key) : "";
  const sslCert = ssl?.cert ? await encodeFileContent(ssl.cert) : "";

  const instanceId = await NativeInterface.createInstance(
    hostname,
    port,
    sslKey,
    sslCert,
  );

  const server = new EmbeddedServer(instanceId);

  servers.set(instanceId, { server, fetch: fetch as never });

  await server.start();

  return server;
}
