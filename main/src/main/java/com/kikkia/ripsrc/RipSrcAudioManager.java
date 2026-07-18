package com.kikkia.ripsrc;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.kikkia.ripsrc.utils.HttpClientUtils;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class RipSrcAudioManager implements HttpConfigurable, AudioSourceManager, AudioSearchManager {
 	public static final String SEARCH_PREFIX = "ripsearch:";
	public static final String ISRC_PREFIX = "ripisrc:";
	public static final String TRACK_PREFIX = "riptrack:";
	private final String baseUrl;
	private final String key;
	private final String name;
	private String userAgent = "Ripsrc";
	private String encodedWants = null;

	private final HttpInterfaceManager httpInterfaceManager;
	private static final Logger log = LoggerFactory.getLogger(RipSrcAudioManager.class);

	public RipSrcAudioManager(String key,
							  String baseUrl,
							  @Nullable String name,
							  @Nullable String userAgent,
							  List<String> wants,
							  int connectTimeout,
							  int socketTimeout,
							  int connectRequestTimeout) {
		this.key = key;
		this.name = name;
		this.baseUrl = baseUrl;

		if (userAgent != null) {
			this.userAgent = userAgent;
		}

		if (!wants.isEmpty()) {
			this.encodedWants = URLEncoder.encode(String.join(",", wants), StandardCharsets.UTF_8);
		}

		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(connectRequestTimeout)
				.setConnectTimeout(connectTimeout)
				.setSocketTimeout(socketTimeout)
				.build();

		this.httpInterfaceManager = HttpClientUtils.createDefaultThreadLocalManager(requestConfig);
	}

	@Override
	@NotNull
	public String getSourceName() {
		return name != null ? name : "ripsrc";
	}

	@Override
	@Nullable
	public AudioSearchResult loadSearch(@NotNull String s, @NotNull Set<AudioSearchResult.Type> set) {
		if (!set.isEmpty() && !set.stream().allMatch(it -> it.equals(AudioSearchResult.Type.TRACK))) {
			throw new RuntimeException(getSourceName() + " can only search tracks");
		}

		if (s.startsWith(SEARCH_PREFIX)) {
			AudioItem result = this.getSearch(s.substring(SEARCH_PREFIX.length()));

			if (result == AudioReference.NO_TRACK) {
				return AudioSearchResult.EMPTY;
			}

			BasicAudioPlaylist playlist = (BasicAudioPlaylist) result;
			return new BasicAudioSearchResult(playlist.getTracks(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
		}

		return null;
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
	public void encodeTrack(AudioTrack audioTrack, DataOutput dataOutput) {
		// Nothing to do
	}

	public AudioItem loadItem(String identifier) {
		if (identifier.startsWith(SEARCH_PREFIX)) {
			return this.getSearch(identifier.substring(SEARCH_PREFIX.length()));
		}

		if (identifier.startsWith(ISRC_PREFIX)) {
			return this.getTracksByISRC(identifier.substring(ISRC_PREFIX.length()));
		}

		if (identifier.startsWith(TRACK_PREFIX)) {
			return this.getTracksById(identifier.substring(TRACK_PREFIX.length()));
		}

		return null;
	}

	public AudioItem getTracksById(String id) {
		return this.loadTracks("tracks", id, false);
	}

	public AudioItem getTracksByISRC(String isrcs) {
		return this.loadTracks("isrcs", isrcs, false);
	}

	public AudioItem getSearch(String query) {
		return this.loadTracks("q", query, true);
	}

	private AudioItem loadTracks(String key, String value, boolean isSearch) {
		var tracks = parseTracks(this.getSearch(key, value));

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		if (tracks.size() == 1 && !isSearch) {
			return tracks.get(0);
		}

		return new BasicAudioPlaylist(
			isSearch ? "Custom Search: " + value : "Track List",
			tracks,
			null,
			isSearch
		);
	}

	@Nullable
	private AudioTrack parseTrack(JsonBrowser json) {
		var id = json.get("id").text();

		if (id == null) {
			return null;
		}

		var version = json.get("versions").index(0);
		var codec = version.get("codec").text();
		var contentLength = version.get("size").asLong(Units.CONTENT_LENGTH_UNKNOWN);

		String url;
		try {
			url = new URIBuilder(version.get("url").text())
				.addParameter("codec", codec)
				.addParameter("clen", String.valueOf(contentLength))
				.build()
				.toString();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		JsonBrowser isrcs = json.get("isrc");
		String isrc = selectBestIsrc(isrcs);

		var track = new AudioTrackInfo(
			json.get("title").text(),
			json.get("artist").text(),
			json.get("duration").asLong(0) * 1000,
			id,
			false,
			url,
			json.get("picture").text(),
			isrc
		);

		return new RipSrcAudioTrack(track, this);
	}

	@Nullable
	private String selectBestIsrc(JsonBrowser isrcs) {
		return isrcs.values()
			.stream()
			// we sometimes get ISRCs that aren't actually valid.
			// we could add a regex check here, but I don't think it's necessary currently.
			.filter(isrc -> isrc.text() != null && isrc.text().length() == 12)
			.findFirst()
			.map(isrc -> isrc.text().toUpperCase(Locale.ROOT))
			.orElse(null);
	}

	@NotNull
	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();

		for (var track : json.values()) {
			var ripSrcTrack = this.parseTrack(track);

			if (ripSrcTrack != null) {
				tracks.add(ripSrcTrack);
			}
		}

		return tracks;
	}

	@NotNull
	private URI prepareRequestUri(String key, String value) {
		try {
			var builder = new URIBuilder(this.baseUrl)
				.addParameter(key, URLEncoder.encode(value, StandardCharsets.UTF_8))
				.addParameter("p", this.key);

			if (this.encodedWants != null) {
				builder.addParameter("wants", this.encodedWants);
			}

			return builder.build();
		} catch (URISyntaxException exception) {
			throw ExceptionTools.toRuntimeException(exception);
		}
	}

	@NotNull
	private JsonBrowser getSearch(String key, String value) {
		var requestUri = this.prepareRequestUri(key, value);
		var request = new HttpGet(requestUri);
		request.addHeader("Accept", "application/json");
		request.addHeader("User-Agent", this.userAgent);

		log.debug("Performing HTTP request with {}", request);

		try (var httpInterface = getHttpInterface();
			var response = httpInterface.execute(request)) {
			HttpClientTools.assertSuccessWithContent(response, "track search");
			HttpClientTools.assertJsonContentType(response);

			var json = JsonBrowser.parse(response.getEntity().getContent());
			log.debug("Received JSON response\n{}", json.format());
			return json;
		} catch (IOException e) {
			throw ExceptionTools.toRuntimeException(e);
		}
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo audioTrackInfo, DataInput dataInput) throws IOException {
		return new RipSrcAudioTrack(audioTrackInfo, this);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
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

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}
 }
