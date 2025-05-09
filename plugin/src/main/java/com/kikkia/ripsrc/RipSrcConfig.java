package com.kikkia.ripsrc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.ripsrc")
@Component
public class RipSrcConfig {
	private String key;
	private String baseUrl;
	private String name;
	private String userAgent;
	private boolean hideBaseUrl;

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public boolean hideBaseUrl() {
		return hideBaseUrl;
	}

	public void setHideBaseUrl(boolean hideBaseUrl) {
		this.hideBaseUrl = hideBaseUrl;
	}
}
