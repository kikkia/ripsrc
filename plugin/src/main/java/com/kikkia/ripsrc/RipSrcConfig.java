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
	private boolean external;
	private int connectTimeout = 3000;
	private int socketTimeout = 3000;
	private int connectionRequestTimeout = 3000;
	
	private boolean caching = false;
	private int cacheMemoryLimitMB = 100;

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

	public void setExternal(boolean external) {
		this.external = external;
	}

	public boolean getExternal() {
		return external;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getSocketTimeout() {
		return socketTimeout;
	}

	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public int getConnectionRequestTimeout() {
		return connectionRequestTimeout;
	}

	public void setConnectionRequestTimeout(int connectionRequestTimeout) {
		this.connectionRequestTimeout = connectionRequestTimeout;
	}

	public boolean isCaching() {
		return caching;
	}

	public void setCaching(boolean caching) {
		this.caching = caching;
	}

	public int getCacheMemoryLimitMB() {
		return cacheMemoryLimitMB;
	}

	public void setCacheMemoryLimitMB(int cacheMemoryLimitMB) {
		this.cacheMemoryLimitMB = cacheMemoryLimitMB;
	}
}
