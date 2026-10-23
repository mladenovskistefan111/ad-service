# Known limitations

- The ad catalog is compiled in; changing ads means a new image.
- `getRandom()` samples with replacement, so the two random ads can be identical.
- Telemetry/profiling are always on (no kill switch); point the endpoints at something reachable or expect periodic exporter error logs.
- Graceful shutdown calls `server.shutdown()` but does not await in-flight RPCs; set a short `terminationGracePeriodSeconds` (10–15 s is plenty).
