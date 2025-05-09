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
    baseurl: "https://xxxxxx.xxxxxx/search"
    useragent: "your-useragent"
    external: false # set to true if you want to enable external results on all isrc searches (may be slow, use with caution)
```

### Usage
#### Lavasrc
```yaml
providers:
  - "ripisrc:%ISRC%"
  - "ripsearch:%QUERY%"
```

#### Other
Using the prefixes "ripisrc:" or "ripsearch:" you can search by title or isrc