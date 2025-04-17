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
    key: xyz
    baseurl: https://xxxxxx.xxxxxx/search
    useragent: your-useragent
```

### Usage
#### Lavasrc
```yaml
providers:
  - "ripisrc:%ISRC%"
```

#### Other
Using the prefixes "ripisrc:" or "ripsearch:" you can search by title or isrc