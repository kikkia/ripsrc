package com.kikkia.ripsrc;

import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RipSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(RipSrcPlugin.class);

    private final RipSrcAudioManager ripSrcAudioManager;

	public RipSrcPlugin(RipSrcConfig config) {
		log.info("Loading RipSrc plugin...");

        this.ripSrcAudioManager = new RipSrcAudioManager(
			config.getKey(),
			config.getBaseUrl(),
			config.getName(),
			config.getUserAgent()
		);
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
        log.info("Registering ripsrc audio source manager...");
		manager.registerSourceManager(this.ripSrcAudioManager);
		return manager;
	}

	@Override
	@NotNull
	public SearchManager configure(@NotNull SearchManager manager) {
		log.info("Registering ripsrc search manager");
		manager.registerSearchManager(this.ripSrcAudioManager);
		return manager;
	}
}
