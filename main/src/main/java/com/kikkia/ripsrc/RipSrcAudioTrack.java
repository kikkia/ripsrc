package com.kikkia.ripsrc;

import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RipSrcAudioTrack extends DelegatedAudioTrack {
	private final RipSrcAudioManager audioManager;

	public RipSrcAudioTrack(AudioTrackInfo trackInfo, RipSrcAudioManager manager) {
		super(trackInfo);
		this.audioManager = manager;
	}

	@Override
	public void process(LocalAudioTrackExecutor localAudioTrackExecutor) throws Exception {
		var initialStreamUrl = getStreamUrl();

		try (HttpInterface httpInterface = audioManager.getHttpInterface()) {
			process0(localAudioTrackExecutor, httpInterface, initialStreamUrl, 0, 1);
		}
	}

	private void process0(
		LocalAudioTrackExecutor executor,
		HttpInterface httpInterface,
		String streamUrl,
		long streamPosition,
		int retryAttempt
	) throws Exception {
		var queryParams = parseQueryParams(streamUrl);

		var codec = queryParams.get("codec");
		var contentLength = queryParams.get("clen");
		var lastStreamPosition = streamPosition;

		try (var stream = new PersistentHttpStream(httpInterface, new URI(streamUrl), contentLength != null ? Long.parseLong(contentLength) : Units.CONTENT_LENGTH_UNKNOWN)) {
			BaseAudioTrack track;

			// Atm only see mpeg or webm
			if (codec == null || codec.contains("opus")) {
				track = new MatroskaAudioTrack(this.trackInfo, stream);
			} else if (codec.contains("mp4a")) {
				track = new MpegAudioTrack(this.trackInfo, stream);
			} else {
				throw new RuntimeException("Unsupported audio codec " + codec);
			}

			if (streamPosition > 0) stream.seek(streamPosition);

			try {
				processDelegate(track, executor);
				// successful play, bail.
				return;
			} catch (RuntimeException e) {
				// unexpected error or ran out of retries.
				if (!"Not success status code: 403".equals(e.getMessage()) || retryAttempt >= 3) {
					throw e;
				}

				lastStreamPosition = stream.getPosition();
			}
		}

		// cycle here so that we don't keep the old stream open
		this.process0(executor, httpInterface, getStreamUrl(), lastStreamPosition, retryAttempt + 1);
	}

	@NotNull
	private String getStreamUrl() {
		var currentUrl = this.trackInfo.uri;
		var queryParams = parseQueryParams(currentUrl);
		var expires = queryParams.get("expires");

		if (expires == null) {
			// we could just try and play it and safe and regenerate the URL
			// but if we can't safely determine the expiration, we want to avoid
			// spamming the server as much as possible.
			return currentUrl;
		}

		try {
			long expireEpochMs = Long.parseLong(expires);

			// safety buffer
			if (System.currentTimeMillis() < expireEpochMs - TimeUnit.MINUTES.toMillis(5)) {
				return currentUrl;
			}
		} catch (NumberFormatException e) {
			return currentUrl;
		}

		var renewedTrack = this.audioManager.getTracksById(trackInfo.identifier);

		if (!(renewedTrack instanceof RipSrcAudioTrack)) {
			throw new RuntimeException("Failed to regenerate stream URL");
		}

		return ((RipSrcAudioTrack) renewedTrack).trackInfo.uri;
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.audioManager;
	}

	private static Map<String, String> parseQueryParams(String uri) {
		try {
			var parsedUri = new URI(uri);
			var queryParams = parsedUri.getQuery();
			var parsedParams = new HashMap<String, String>();
			if (queryParams == null) return parsedParams;

			for (var pair : queryParams.split("&")) {
				var splitIdx = pair.indexOf("=");
				var key = splitIdx > 0 ? pair.substring(0, splitIdx) : pair;
				var value = splitIdx > 0 && pair.length() > splitIdx + 1 ? pair.substring(splitIdx + 1) : null;
				parsedParams.put(key, value);
			}

			return parsedParams;
		} catch (URISyntaxException e) {
			throw ExceptionTools.toRuntimeException(e);
		}
	}
}
