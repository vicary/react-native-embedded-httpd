# react-native-embedded-httpd

A React Native wrapper for creating embedded http servers in a mobile app:

1. Android: ktor@3.1.3
2. iOS: TBC

It is specifically designed to match Hono's API.

## Usage

```tsx
import { serve } from "react-native-embedded-httpd";

const appLike = {
  async fetch(request, env, ctx) {
    return new Response("Hello World!");
  },
  port: 8080,
};

const server = serve(appLike);

// To gracefully stop the server...
await server.close();
```

## Building the example app

1. `yarn install`
1. `cd workspaces/example-app`
1. Prebuild in New Architecture by running `yarn arch:new`, or run
   `yarn arch:old` to test in legacy environments.
1. Run `yarn android` to test in Android
1. Run `yarn ios` to test in iOS

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the
repository and the development workflow.

If you use this library at work, consider
[sponsoring](https://github.com/sponsors/vicary) for a first-class technical
support.
