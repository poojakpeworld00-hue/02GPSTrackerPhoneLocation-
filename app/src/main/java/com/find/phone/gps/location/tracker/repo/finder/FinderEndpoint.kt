package com.find.phone.gps.location.tracker.repo.finder

import android.content.Context
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.Obscured
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.helpers.addHttpInspector
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface FinderEndpoint {

    @GET("similar-phone-number")
    suspend fun similarPhoneNumber(@Query("phone") phone: String): Response<HandsetReply>
}

object FinderHttpClient {

    private const val DEFAULT_BASE_URL = "https://contact-saver.dailymorningupdate.com/"
    private const val HEADER_API_KEY = "x-api-key"

    private val http: OkHttpClient by lazy {
        val apiKey = Obscured.s(BuildConfig.LOOKUP_API_KEY)
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header(HEADER_API_KEY, apiKey).build())
            }
            .addHttpInspector(HEADER_API_KEY)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader(HEADER_API_KEY)
                    })
                }
            }
            .build()
    }

    @Volatile private var cached: Pair<String, FinderEndpoint>? = null

    fun api(context: Context): FinderEndpoint {
        val base = baseUrl(context)
        cached?.takeIf { it.first == base }?.let { return it.second }
        val api = Retrofit.Builder()
            .baseUrl(base)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinderEndpoint::class.java)
        cached = base to api
        return api
    }

    private fun baseUrl(context: Context): String {
        val remote = AdPrefsStore.getInstance(context).getString("contacts_base_url")?.trim().orEmpty()
        val withSlash = if (remote.endsWith("/")) remote else "$remote/"
        return if (remote.isNotEmpty() && withSlash.toHttpUrlOrNull() != null) withSlash else DEFAULT_BASE_URL
    }
}
