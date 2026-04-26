package com.gamaxtersnow.mytv

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamaxtersnow.mytv.Utils.getDateFormat
import com.gamaxtersnow.mytv.api.ApiClient
import com.gamaxtersnow.mytv.api.FAuth
import com.gamaxtersnow.mytv.api.FAuthService
import com.gamaxtersnow.mytv.api.FEPG
import com.gamaxtersnow.mytv.models.TVViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


object Request {
    private const val TAG = "Request"
    private var fAuthService: FAuthService = ApiClient().fAuthService

    private var tokenFH = ""

    private val handler = Handler(Looper.getMainLooper())

    private var callFAuth: Call<FAuth>? = null

    fun onDestroy() {
        Log.i(TAG, "onDestroy")
    }

    private fun cancelCall() {
        callFAuth?.cancel()
    }

    private fun fetchFAuth(tvModel: TVViewModel) {
        cancelCall()

        val title = tvModel.getTV().title

        var qa = "HD"
        if (tokenFH != "") {
            qa = "FHD"
        }

        callFAuth = fAuthService.getAuth(tokenFH, tvModel.getTV().pid, qa)
        callFAuth?.enqueue(object : Callback<FAuth> {
            override fun onResponse(call: Call<FAuth>, response: Response<FAuth>) {
                if (response.isSuccessful && response.body()?.data?.live_url != null) {
                    val url = response.body()?.data?.live_url!!
                    Log.d(TAG, "$title url $url")
                    tvModel.addVideoUrl(url)
                    tvModel.allReady()
                    tvModel.tokenFHRetryTimes = 0
                } else {
                    Log.e(TAG, "auth status error ${response.code()}")
                    if (tvModel.tokenFHRetryTimes < tvModel.tokenFHRetryMaxTimes) {
                        tvModel.tokenFHRetryTimes++
                        fetchFAuth(tvModel)
                    }
                }
            }

            override fun onFailure(call: Call<FAuth>, t: Throwable) {
                Log.e(TAG, "auth request error $t")
                if (tvModel.tokenFHRetryTimes < tvModel.tokenFHRetryMaxTimes) {
                    tvModel.tokenFHRetryTimes++
                    fetchFAuth(tvModel)
                }
            }
        })
    }

    fun fetchData(tvModel: TVViewModel) {
        if (tvModel.getTV().channel == "港澳台") {
            fetchFAuth(tvModel)
            return
        }
    }

    fun fetchFEPG(tvViewModel: TVViewModel) {
        val title = tvViewModel.getTV().title
        fAuthService.getEPG(tvViewModel.getTV().pid, getDateFormat("yyyyMMdd"))
            .enqueue(object : Callback<List<FEPG>> {
                override fun onResponse(
                    call: Call<List<FEPG>>,
                    response: Response<List<FEPG>>
                ) {
                    if (response.isSuccessful) {
                        val program = response.body()
                        if (program != null) {
                            tvViewModel.addFEPG(program)
                            Log.d(TAG, "$title program ${program.size}")
                        }
                    } else {
                        Log.w(TAG, "$title program error")
                    }
                }

                override fun onFailure(call: Call<List<FEPG>>, t: Throwable) {
                    Log.e(TAG, "$title program request failed $t")
                }
            })
    }

    interface RequestListener {
        fun onRequestFinished(message: String?)
    }

    private var listener: RequestListener? = null

    fun setRequestListener(listener: RequestListener) {
        this.listener = listener
    }
}
