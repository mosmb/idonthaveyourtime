package io.morgan.idonthaveyourtime.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.worker.ModelDownloadService
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build()

    @Provides
    @Singleton
    fun provideModelDownloadService(retrofit: Retrofit): ModelDownloadService =
        retrofit.create(ModelDownloadService::class.java)

    private const val BASE_URL = "https://huggingface.co/"
    private const val CONNECT_TIMEOUT_MS = 20_000L
    private const val READ_TIMEOUT_MS = 30_000L
    private const val USER_AGENT = "idonthaveyourtime/1.0"
}

