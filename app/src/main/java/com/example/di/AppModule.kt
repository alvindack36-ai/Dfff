package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.GatewayApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "simgate_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(prefs: PreferencesManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val dynamicUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            val baseApiUrl = prefs.apiBase.trim()
            if (baseApiUrl.isNotEmpty()) {
                val baseHttpUrl = baseApiUrl.toHttpUrlOrNull()
                if (baseHttpUrl != null) {
                    val originalUrl = request.url
                    val originalSegments = originalUrl.pathSegments
                    val baseSegments = baseHttpUrl.pathSegments
                    
                    val newUrlBuilder = originalUrl.newBuilder()
                        .scheme(baseHttpUrl.scheme)
                        .host(baseHttpUrl.host)
                        .port(baseHttpUrl.port)

                    // Clear original paths
                    val originalCount = originalUrl.pathSize
                    for (i in originalCount - 1 downTo 0) {
                        newUrlBuilder.removePathSegment(i)
                    }

                    // Add base segments
                    for (segment in baseSegments) {
                        if (segment.isNotEmpty()) {
                            newUrlBuilder.addPathSegment(segment)
                        }
                    }

                    // Add original active endpoints ("device-connect", etc.)
                    for (segment in originalSegments) {
                        if (segment.isNotEmpty() && segment != "placeholder.invalid") {
                            newUrlBuilder.addPathSegment(segment)
                        }
                    }

                    request = request.newBuilder().url(newUrlBuilder.build()).build()
                }
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGatewayApi(okHttpClient: OkHttpClient, moshi: Moshi): GatewayApi {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GatewayApi::class.java)
    }
}
