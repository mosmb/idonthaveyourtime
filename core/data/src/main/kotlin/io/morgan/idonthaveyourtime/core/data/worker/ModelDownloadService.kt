package io.morgan.idonthaveyourtime.core.data.worker

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit service used to stream-download model files.
 *
 * This is an internal data-layer API; prefer higher-level data sources and repositories elsewhere.
 */
interface ModelDownloadService {
    @Streaming
    @GET
    suspend fun download(@Url url: String): Response<ResponseBody>
}
