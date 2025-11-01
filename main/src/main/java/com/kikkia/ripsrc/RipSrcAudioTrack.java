package com.kikkia.ripsrc;

import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RipSrcAudioTrack extends DelegatedAudioTrack {
	private final RipSrcAudioManager audioManager;
	private static final Logger log = LoggerFactory.getLogger(RipSrcAudioTrack.class);

	public RipSrcAudioTrack(AudioTrackInfo trackInfo, RipSrcAudioManager manager) {
		super(trackInfo);
		this.audioManager = manager;
	}

	@Override
	public void process(LocalAudioTrackExecutor localAudioTrackExecutor) throws Exception {
		var downloadLink = this.trackInfo.uri;
		var queryParams = parseQueryParams(downloadLink);
		log.info("Downloading {} from {}", this.trackInfo.title, downloadLink);

		var codec = queryParams.get("codec");
		var contentLength = queryParams.get("clen");

		try (var httpInterface = this.audioManager.getHttpInterface();
			 var stream = new PersistentHttpStream(httpInterface, new URI(downloadLink), contentLength != null ? Long.parseLong(contentLength) : Units.CONTENT_LENGTH_UNKNOWN)) {

			// Atm only see mpeg or webm
			if (codec.equals("opus")) {
				processDelegate(new MatroskaAudioTrack(this.trackInfo, stream), localAudioTrackExecutor);
			} else if (codec.equals("mp4a")) {
				processDelegate(new MpegAudioTrack(this.trackInfo, stream), localAudioTrackExecutor);
			} else {
				throw new RuntimeException("Unsupported audio codec " + codec);
			}
		}
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
