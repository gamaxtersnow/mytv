package com.lizongying.mytv.api


import android.os.Build
import android.util.Log
import com.lizongying.mytv.jce.JceConverterFactory
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.protobuf.ProtoConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class ApiClient {
    private val yspUrl = "https://player-api.yangshipin.cn/"
    private val myUrl = "https://lyrics.run/"
    private val protoUrl = "https://capi.yangshipin.cn/"
    private val traceUrl = "https://btrace.yangshipin.cn/"
    private val trace2Url = "https://aatc-api.yangshipin.cn/"
    private val trace3Url = "https://dtrace.ysp.cctv.cn/"
    private val jceUrl = "https://jacc.ysp.cctv.cn/"
    private val fUrl = "https://m.fengshows.com/"

    private var okHttpClient = getUnsafeOkHttpClient()

    val yspApiService: YSPApiService by lazy {
        Retrofit.Builder()
            .baseUrl(yspUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YSPApiService::class.java)
    }

    val yspTokenService: YSPTokenService by lazy {
        Retrofit.Builder()
            .baseUrl(myUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YSPTokenService::class.java)
    }

    val releaseService: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(myUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ReleaseService::class.java)
    }

    val yspProtoService: YSPProtoService by lazy {
        Retrofit.Builder()
            .baseUrl(protoUrl)
            .client(okHttpClient)
            .addConverterFactory(ProtoConverterFactory.create())
            .build().create(YSPProtoService::class.java)
    }

    val yspBtraceService: YSPBtraceService by lazy {
        Retrofit.Builder()
            .baseUrl(traceUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YSPBtraceService::class.java)
    }

    val yspBtraceService2: YSPBtraceService by lazy {
        Retrofit.Builder()
            .baseUrl(trace2Url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YSPBtraceService::class.java)
    }

    val yspBtraceService3: YSPBtraceService by lazy {
        Retrofit.Builder()
            .baseUrl(trace3Url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(YSPBtraceService::class.java)
    }

    val yspJceService: YSPJceService by lazy {
        Retrofit.Builder()
            .baseUrl(jceUrl)
            .client(okHttpClient)
            .addConverterFactory(JceConverterFactory.create())
            .build().create(YSPJceService::class.java)
    }

    val fAuthService: FAuthService by lazy {
        Retrofit.Builder()
            .baseUrl(fUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(FAuthService::class.java)
    }

    private fun enableTls12OnPreLollipop(client: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                val sc = SSLContext.getInstance("TLSv1.2")

                // 使用默认的信任管理器
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                val trustManager = trustManagers[0] as X509TrustManager

                sc.init(null, null, null)

                // 使用新的sslSocketFactory重载方法，同时传递信任管理器
                client.sslSocketFactory(Tls12SocketFactory(sc.socketFactory), trustManager)

                val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()

                val specs: MutableList<ConnectionSpec> = ArrayList()
                specs.add(cs)
                specs.add(ConnectionSpec.COMPATIBLE_TLS)
                specs.add(ConnectionSpec.CLEARTEXT)

                client.connectionSpecs(specs)
            } catch (exc: java.lang.Exception) {
                Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc)
            }
        }

        return client
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts: Array<TrustManager> = arrayOf(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out java.security.cert.X509Certificate>?,
                        authType: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<out java.security.cert.X509Certificate>?,
                        authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                        return emptyArray()
                    }
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val builder = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .dns(DnsCache())

            return enableTls12OnPreLollipop(builder).build()

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}