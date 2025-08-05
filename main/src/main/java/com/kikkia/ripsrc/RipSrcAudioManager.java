package com.kikkia.ripsrc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.kikkia.ripsrc.utils.HttpClientUtils;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RipSrcAudioManager implements HttpConfigurable, AudioSourceManager, AudioSearchManager {
	public static final String SEARCH_PREFIX = "ripsearch:";
	public static final String ISRC_PREFIX = "ripisrc:";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK);
	private String baseUrl;
	private String key;
	private String name;
	private String userAgent = "Lavasrc";
	private boolean external;

	private HttpInterfaceManager httpInterfaceManager;
	private static final Logger log = LoggerFactory.getLogger(RipSrcAudioManager.class);
	
	private final Cache<String, CachedTrackResult> isrcCache;
	private static final Pattern EXPIRES_PATTERN = Pattern.compile("expires=(\\d+)");
	private final ScheduledExecutorService statsExecutor;

	public RipSrcAudioManager(String key,
							  String baseUrl,
							  @Nullable String name,
							  @Nullable String userAgent,
							  boolean external,
							  int connectTimeout,
							  int socketTimeout,
							  int connectRequestTimeout,
							  boolean cachingEnabled,
							  int cacheMemoryLimitMB) {
		this.key = key;
		this.name = name;
		this.baseUrl = baseUrl;
		if (userAgent != null) {
			this.userAgent = userAgent;
		}
		this.external = external;

		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(connectRequestTimeout)
				.setConnectTimeout(connectTimeout)
				.setSocketTimeout(socketTimeout)
				.build();

		this.httpInterfaceManager = HttpClientUtils.createDefaultThreadLocalManager(requestConfig);
		
		if (cachingEnabled) {
			log.info("Initializing ISRC cache with {}MB memory limit", cacheMemoryLimitMB);
			
			var cacheBuilder = Caffeine.newBuilder()
					.expireAfter(new CustomExpiry());
			
			long memoryLimitBytes = cacheMemoryLimitMB * 1024L * 1024L;
			cacheBuilder.maximumWeight(memoryLimitBytes)
					.weigher((cacheKey, cacheValue) -> 2500);
			
			cacheBuilder.recordStats();
			
			this.isrcCache = cacheBuilder.build();
			
			this.statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "RipSrc-Cache-Stats");
				t.setDaemon(true);
				return t;
			});
			
			this.statsExecutor.scheduleAtFixedRate(this::logCacheStats, 30, 30, TimeUnit.SECONDS);
			log.debug("Cache stats logging scheduled every 30 seconds");
		} else {
			log.info("ISRC caching disabled");
			this.isrcCache = null;
			this.statsExecutor = null;
		}
	}

	@Override
	public String getSourceName() {
		return name != null ? name : "ripsrc";
	}

	@Override
	public @Nullable AudioSearchResult loadSearch(@NotNull String s, @NotNull Set<AudioSearchResult.Type> set) {
		if (!set.isEmpty() && !set.stream().allMatch(it -> it.equals(AudioSearchResult.Type.TRACK))) {
			throw new RuntimeException(getSourceName() + " can only search tracks");
		}
		try {
			if (s.startsWith(SEARCH_PREFIX)) {
				return getSearchResults(s.substring(SEARCH_PREFIX.length()));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	private AudioSearchResult getSearchResults(String s) throws IOException {
		var json = getJson(getSearchUrl(s));
		var tracks = parseTracks(json);
		return new BasicAudioSearchResult(tracks, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager audioPlayerManager, AudioReference audioReference) {
		return this.loadItem(audioReference.identifier);
	}

	@Override
	public boolean isTrackEncodable(AudioTrack audioTrack) {
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack audioTrack, DataOutput dataOutput) throws IOException {
		// Nothing to do
	}

	public AudioItem loadItem(String identifier) {
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()));
			}

			if (identifier.startsWith(ISRC_PREFIX)) {
				return this.getTrackByISRC(identifier.substring(ISRC_PREFIX.length()));
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private AudioItem getTrackByISRC(String isrc) throws IOException {
		if (isrc == null || isrc.trim().isEmpty()) {
			log.warn("Empty or null ISRC provided");
			return AudioReference.NO_TRACK;
		}
		
		if (isrcCache != null) {
			CachedTrackResult cachedResult = isrcCache.getIfPresent(isrc);
			if (cachedResult != null) {
				log.debug("Cache hit for ISRC: {}", isrc);
				return this.parseTrack(cachedResult.getTrackData());
			}
		}
		
		log.debug("Cache miss for ISRC: {}, fetching from API", isrc);
		
		try {
			var json = this.getJson(getISRCSearchUrl(URLEncoder.encode(isrc, StandardCharsets.UTF_8)));
			if (json == null || json.values().isEmpty() || json.index(0).get("id").isNull()) {
				log.debug("No track found for ISRC: {}", isrc);
				return AudioReference.NO_TRACK;
			}
			
			var trackData = json.index(0);
			
			long expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
			if (!trackData.get("versions").values().isEmpty()) {
				String url = trackData.get("versions").index(0).get("url").text();
				if (url != null && !url.isEmpty()) {
					expiresAt = extractExpiresFromUrl(url);
				}
			}
			
			if (isrcCache != null) {
				CachedTrackResult newCachedResult = new CachedTrackResult(trackData, expiresAt);
				
				var trackIsrcs = trackData.get("isrc");
				List<String> allIsrcs = new ArrayList<>();
				
				if (trackIsrcs != null && !trackIsrcs.values().isEmpty()) {
					for (var trackIsrc : trackIsrcs.values()) {
						String isrcValue = trackIsrc.text();
						if (isrcValue != null && !isrcValue.trim().isEmpty()) {
							allIsrcs.add(isrcValue);
						}
					}
				}
				
				if (allIsrcs.isEmpty()) {
					allIsrcs.add(isrc);
				}
				
				int cachedCount = 0;
				long timeLeft = expiresAt - System.currentTimeMillis();
				String timeLeftFormatted = formatDuration(timeLeft);
				
				for (String isrcValue : allIsrcs) {
					isrcCache.put(isrcValue, newCachedResult);
					cachedCount++;
				}
				
				log.debug("Cached {} ISRC(s) for track '{}' - Time left: {} - ISRCs: {}", 
					cachedCount, trackData.get("title").text(), timeLeftFormatted, allIsrcs);
			}
			
			return this.parseTrack(trackData);
		} catch (Exception e) {
			log.error("Failed to fetch track for ISRC: {}", isrc, e);
			return AudioReference.NO_TRACK;
		}
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(getSearchUrl(URLEncoder.encode(query, StandardCharsets.UTF_8)));
		if (json == null || json.values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Custom Search: " + query, this.parseTracks(json), null, true);
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		var id = json.get("id").text();
		var url = json.get("versions").index(0).get("url").text() + "&codec=" + json.get("versions").index(0).get("codec").text();
		var track = new AudioTrackInfo(
			json.get("title").text(),
			json.get("artist").text(),
			json.get("duration").asLong(0) * 1000,
			id,
			false,
			url,
			json.get("picture").text(),
			json.get("isrc").index(0).text()
		);
		return new RipSrcAudioTrack(track, this);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.values()) {
			tracks.add(this.parseTrack(track));
		}
		return tracks;
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo audioTrackInfo, DataInput dataInput) throws IOException {
		return new RipSrcAudioTrack(audioTrackInfo, this);
	}

	@Override
	public void shutdown() {
		if (statsExecutor != null && !statsExecutor.isShutdown()) {
			statsExecutor.shutdown();
			try {
				if (!statsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					statsExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				statsExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
		
		if (isrcCache != null) {
			logCacheStats();
			isrcCache.invalidateAll();
			log.info("ISRC cache shutdown complete");
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> function) {
		this.httpInterfaceManager.configureRequests(function);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> consumer) {
		this.httpInterfaceManager.configureBuilder(consumer);
	}

	public JsonBrowser getJson(String uri) throws IOException {
		try (HttpInterface httpInterface = getHttpInterface()) {
			var request = new HttpGet(uri);
			request.setHeader("Accept", "application/json");
			request.setHeader("User-Agent", userAgent);
			return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
		}
	}

	public String getISRCSearchUrl(String isrc) {
		return baseUrl + "?p=" + key + "&isrcs=" + isrc + "&external=" + external;
	}

	public String getSearchUrl(String search) {
		return baseUrl + "?p=" + key + "&q=" + search;
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}
	
	private long extractExpiresFromUrl(String url) {
		try {
			Matcher matcher = EXPIRES_PATTERN.matcher(url);
			if (matcher.find()) {
				long expires = Long.parseLong(matcher.group(1));
				long now = System.currentTimeMillis();
				if (expires > now) {
					return expires;
				}
				log.warn("Expires timestamp is in the past for URL: {} (expires: {})", url, expires);
			}
		} catch (NumberFormatException e) {
			log.warn("Failed to parse expires timestamp from URL: {}", url, e);
		}
		return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
	}
	

	
	private String formatDuration(long millis) {
		if (millis < 0) return "expired";
		
		long seconds = millis / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long days = hours / 24;
		
		if (days > 0) {
			return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
		} else if (hours > 0) {
			return String.format("%dh %dm", hours, minutes % 60);
		} else if (minutes > 0) {
			return String.format("%dm %ds", minutes, seconds % 60);
		} else {
			return String.format("%ds", seconds);
		}
	}
	
	private void logCacheStats() {
		if (isrcCache == null) return;
		
		try {
			var stats = isrcCache.stats();
			long size = isrcCache.estimatedSize();
			
			if (size > 0 || stats.requestCount() > 0) {
				double hitRate = stats.requestCount() > 0 ? (stats.hitCount() * 100.0) / stats.requestCount() : 0.0;
				
				log.debug("ISRC Cache Stats - Size: {}, Requests: {}, Hits: {}, Misses: {}, Hit Rate: {}%, Evictions: {}", 
					size,
					stats.requestCount(),
					stats.hitCount(),
					stats.missCount(),
					String.format("%.1f", hitRate),
					stats.evictionCount()
				);
				
				if (stats.evictionCount() > 0) {
					log.warn("Cache evictions detected: {}", stats.evictionCount());
				}
			}
		} catch (Exception e) {
			log.warn("Failed to log cache stats", e);
		}
	}
	

	
	public String getCacheStats() {
		if (isrcCache == null) {
			return "ISRC Cache - Disabled";
		}
		
		var stats = isrcCache.stats();
		long size = isrcCache.estimatedSize();
		double hitRate = stats.requestCount() > 0 ? (stats.hitCount() * 100.0) / stats.requestCount() : 0.0;
		
		return String.format("ISRC Cache - Size: %d, Hits: %d, Misses: %d, Hit Rate: %.1f%%, Evictions: %d", 
			size, stats.hitCount(), stats.missCount(), hitRate, stats.evictionCount());
	}
	
	public void clearCache() {
		if (isrcCache != null) {
			isrcCache.invalidateAll();
			log.info("ISRC cache manually cleared");
		} else {
			log.warn("Cannot clear cache - caching is disabled");
		}
	}
	
	private static class CachedTrackResult {
		private final JsonBrowser trackData;
		private final long expiresAt;
		
		public CachedTrackResult(JsonBrowser trackData, long expiresAt) {
			this.trackData = trackData;
			this.expiresAt = expiresAt;
		}
		
		public JsonBrowser getTrackData() {
			return trackData;
		}
		
		public long getExpiresAt() {
			return expiresAt;
		}
	}
	
	private class CustomExpiry implements Expiry<String, CachedTrackResult> {
		@Override
		public long expireAfterCreate(String key, CachedTrackResult value, long currentTime) {
			long ttl = value.getExpiresAt() - System.currentTimeMillis();
			return ttl > 0 ? TimeUnit.MILLISECONDS.toNanos(ttl) : TimeUnit.SECONDS.toNanos(1);
		}
		
		@Override
		public long expireAfterUpdate(String key, CachedTrackResult value, long currentTime, long currentDuration) {
			return expireAfterCreate(key, value, currentTime);
		}
		
		@Override
		public long expireAfterRead(String key, CachedTrackResult value, long currentTime, long currentDuration) {
			return currentDuration;
		}
	}
}
