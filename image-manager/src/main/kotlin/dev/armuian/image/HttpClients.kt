package dev.armuian.image

import dev.armuian.core.AppInfo
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {
    fun create(userAgent: String = "ArmUian/0.1 (+${AppInfo.GITHUB_REPO_URL})"): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
            .build()
}
