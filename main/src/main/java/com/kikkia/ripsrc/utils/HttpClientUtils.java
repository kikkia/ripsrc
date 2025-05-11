package com.kikkia.ripsrc.utils;

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpClientBuilder;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;

public class HttpClientUtils {

    private static HttpClientBuilder createHttpBuilder(RequestConfig requestConfig) {
        CookieStore cookieStore = new BasicCookieStore();

        return new ExtendedHttpClientBuilder()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(requestConfig);
    }

    public static HttpInterfaceManager createDefaultThreadLocalManager(RequestConfig requestConfig) {
        return new ThreadLocalHttpInterfaceManager(createHttpBuilder(requestConfig), requestConfig);
    }

}
