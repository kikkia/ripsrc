## Ripsrc
A plugin for messing around with a new source on ll

Add it to LL:
```yaml
- dependency: "com.kikkia.ripsrc:ripsrc-plugin:{current commit hash}"
  repository: "https://maven.kikkia.dev/snapshots"
```

### Config:
```yaml
plugins:
  ripsrc:
    key: "xyz"
    baseUrl: "https://xxxxxx.xxxxxx/search"
    name: "ripsrc" # optional, custom source name (defaults to "ripsrc")
    userAgent: "your-useragent" # optional, defaults to "Lavasrc"
    external: false # set to true if you want to enable external results on all isrc searches (may be slow, use with caution)
    caching: true # optional, enables ISRC caching (defaults to false)
    cacheMemoryLimitMB: 100 # optional, cache memory limit in MB (defaults to 100MB)
    connectTimeout: 3000 # optional, connection timeout in milliseconds (defaults to 3000)
    socketTimeout: 3000 # optional, socket timeout in milliseconds (defaults to 3000)
    connectionRequestTimeout: 3000 # optional, connection request timeout in milliseconds (defaults to 3000)
```

#### Caching Configuration
- **`caching`**: Enables ISRC caching with URL-based expiry
- **`cacheMemoryLimitMB`**: Controls maximum memory usage for the cache (approximate usage, use with caution)
- When caching is enabled, tracks are cached until their URLs expire
- Multiple ISRCs from the same track response are cached together

### Usage
#### Lavasrc
```yaml
providers:
  - "ripisrc:%ISRC%"
  - "ripsearch:%QUERY%"
```

#### Other
Using the prefixes "ripisrc:" or "ripsearch:" you can search by title or isrc