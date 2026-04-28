package com.gamaxtersnow.mytv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.gamaxtersnow.mytv.databinding.PlayerBinding
import com.gamaxtersnow.mytv.models.TVViewModel
import com.gamaxtersnow.mytv.UnifiedVideoPlayer
import com.gamaxtersnow.mytv.Utils
import com.gamaxtersnow.mytv.MainActivity
import com.gamaxtersnow.mytv.ui.AppUiMode

/**
 * 播放器Fragment
 * 使用统一播放器架构，职责分离，符合MVVM模式
 */
class PlayerFragment : Fragment(), SurfaceHolder.Callback {

    private var _binding: PlayerBinding? = null
    private var playerView: PlayerView? = null
    private var tvViewModel: TVViewModel? = null
    private val aspectRatio = 16f / 9f

    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private var isSurfaceCreated = false

    // 控制器
    private lateinit var playerController: PlayerController
    private lateinit var uiController: PlayerUiController
    private lateinit var errorRecoveryManager: ErrorRecoveryManager
    private var uiMode: AppUiMode? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        val rootView = _binding!!.root

        // 初始化两种视图，根据播放器类型动态切换
        surfaceView = _binding!!.surfaceView
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
        // 设置SurfaceHolder像素格式，OPAQUE让系统选择最优硬件格式（4K减少GPU合成开销）
        surfaceHolder.setFormat(android.graphics.PixelFormat.OPAQUE)
        // 将SurfaceView置于顶层
        surfaceView.setZOrderOnTop(false)
        surfaceView.setZOrderMediaOverlay(true)

        playerView = _binding!!.playerView

        if (Utils.isTmallDevice()) {
            // 天猫设备默认使用SurfaceView
            _binding!!.playerView.visibility = View.INVISIBLE
        } else {
            // 其他设备默认使用PlayerView，播放RTP时自动切换
            _binding!!.surfaceView.visibility = View.INVISIBLE
        }

        playerView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                playerView!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
                // PlayerView的尺寸调整逻辑可以放在这里
            }
        })

        // 初始化控制器
        playerController = PlayerController(requireContext())

        // 设置视频输出
        playerController.setSurfaceHolder(surfaceHolder)
        if (playerView != null) {
            playerController.setPlayerView(playerView!!)
        }

        // 播放器类型变化回调（仅ExoPlayer）
        playerController.setPlayerTypeChangeListener { _ ->
            activity?.runOnUiThread {
                _binding?.surfaceView?.visibility = View.INVISIBLE
                _binding?.playerView?.visibility = View.VISIBLE
                _binding?.playerView?.bringToFront()
                Log.d(TAG, "使用ExoPlayer，显示PlayerView")
            }
        }
        uiController = PlayerUiController(rootView, object : PlayerUiController.PlayerUiListener {
            override fun onSwitchPlayerRequested(type: UnifiedVideoPlayer.PlayerType) {
                playerController.switchPlayer(type)
                uiController.updatePlayerTypeSelection(type)
            }

            override fun onSettingsApplied(cacheMs: Int, performanceMode: PerformanceConfig.PerformanceMode) {
                playerController.applySettings(cacheMs, performanceMode)
            }

            override fun onSettingsReset() {
                // 重置逻辑已在UIController内部处理
            }

            override fun onExportPerformanceReport() {
                // 导出报告逻辑
                Log.i(TAG, "导出性能报告")
            }

            override fun onClearPerformanceAlerts() {
                // 清除告警逻辑已在UIController内部处理
            }

            override fun showToast(message: String) {
                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
            }

            override fun onStatusUpdateRequested() {
                // 更新UI状态
                uiController.updatePlayerStatus(
                    playerController.getPlayerType(),
                    playerController.getPlayerState(),
                    playerController.getCurrentUrl()
                )
            }

            override fun onPauseRequested() {
                playerController.pause()
            }

            override fun onResumeRequested() {
                playerController.resume()
            }

            override fun onStopRequested() {
                playerController.stop()
            }

            override fun onReplayRequested() {
                playerController.replay()
            }

            override fun onPlaylistRequested() {
                (activity as? MainActivity)?.toggleChannelPanel()
            }

            override fun onEpgRequested() {
                (activity as? MainActivity)?.toggleEpgPanel()
            }

            override fun onSettingsRequested() {
                (activity as? MainActivity)?.toggleSettingPanel()
            }
        })

        // 初始化错误恢复管理器
        initializeErrorRecovery()

        (activity as MainActivity).fragmentReady("PlayerFragment")
        return rootView
    }

    /**
     * 初始化错误恢复管理器
     */
    private fun initializeErrorRecovery() {
        errorRecoveryManager = ErrorRecoveryManager(requireContext())
        val recoveryConfig = ErrorRecoveryManager.RecoveryConfig(
            maxRetryCount = 3,
            retryDelayMs = 2000,
            networkCheckEnabled = true,
            adaptiveBitrateEnabled = true,
            fallbackToLowerQuality = true
        )
        errorRecoveryManager.initialize(recoveryConfig)

        errorRecoveryManager.setEventListener(object : ErrorRecoveryManager.RecoveryEventListener {
            override fun onErrorDetected(error: ErrorRecoveryManager.ErrorInfo) {
                Log.d(TAG, "错误检测: ${error.type} - ${error.message}")
                uiController.showErrorMessage("错误: ${error.message}")
            }

            override fun onRecoveryStarted(strategy: ErrorRecoveryManager.RecoveryStrategy) {
                Log.d(TAG, "开始恢复: $strategy")
                uiController.updateStatus("恢复中: $strategy")
            }

            override fun onRecoveryCompleted(success: Boolean, message: String) {
                Log.d(TAG, "恢复完成: $success - $message")
                if (success) {
                    uiController.hideErrorMessage()
                    uiController.updateStatus("恢复成功")
                    // 如果恢复成功且当前没有播放，尝试重新播放
                    if (playerController.getCurrentUrl() != null && playerController.getPlayerState() != UnifiedVideoPlayer.PlayerState.PLAYING) {
                        playerController.replay()
                    }
                } else {
                    uiController.showErrorMessage("恢复失败: $message")
                    uiController.updateStatus("恢复失败")
                }
            }

            override fun onNetworkStatusChanged(available: Boolean, networkType: String) {
                Log.d(TAG, "网络状态变化: 可用=$available, 类型=$networkType")
                if (available && playerController.getCurrentUrl() != null && playerController.getPlayerState() != UnifiedVideoPlayer.PlayerState.PLAYING) {
                    // 网络恢复，尝试重新播放
                    Log.d(TAG, "网络恢复，尝试重新播放")
                    playerController.replay()
                }
            }
        })
    }

    @OptIn(UnstableApi::class)
    fun play(tvViewModel: TVViewModel) {
        this.tvViewModel = tvViewModel
        uiController.bindDailyOverlay(tvViewModel)
        val videoUrl = tvViewModel.getVideoUrlCurrent()

        Log.i(TAG, "播放视频: ${tvViewModel.getTV().title}, URL: $videoUrl")

        // 初始化播放器事件监听器
        val playerListener = object : PlayerController.PlayerEventListener {
            override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                Log.d(TAG, "播放器状态变化: $state")
                when (state) {
                    UnifiedVideoPlayer.PlayerState.PLAYING -> {
                        (activity as MainActivity).isPlaying()
                    }
                    UnifiedVideoPlayer.PlayerState.ERROR -> {
                        tvViewModel.changed()
                        errorRecoveryManager.handleError(
                            ErrorRecoveryManager.ErrorInfo(
                                type = ErrorRecoveryManager.ErrorType.PLAYER_ERROR,
                                message = "播放器错误",
                                url = videoUrl
                            )
                        )
                    }
                    else -> {
                        // 其他状态处理
                    }
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "播放错误: $error")
                tvViewModel.changed()
                uiController.showErrorMessage(error)
                errorRecoveryManager.handleError(
                    ErrorRecoveryManager.ErrorInfo(
                        type = ErrorRecoveryManager.ErrorType.NETWORK_ERROR,
                        message = error,
                        url = videoUrl
                    )
                )
            }

            override fun onBufferingUpdate(percent: Int) {
                Log.d(TAG, "缓冲进度: $percent%")
            }

            override fun onProgressUpdate(position: Long, duration: Long) {
                // 更新播放进度（如果需要显示进度条）
            }

            override fun onPlaybackCompleted() {
                Log.d(TAG, "播放完成")
                tvViewModel.changed()
            }

            override fun onPlaying() {
                (activity as MainActivity).isPlaying()
                uiController.hideErrorMessage()
            }
        }

        // 播放视频
        if (isSurfaceCreated || !Utils.isTmallDevice()) {
            playerController.initializePlayer(videoUrl, playerListener)
        } else {
            // Surface未创建，等待Surface创建后再播放
            Log.d(TAG, "Surface未创建，等待Surface创建后再播放")
        }
    }

    fun onUiModeChanged(mode: AppUiMode) {
        uiMode = mode
        if (::uiController.isInitialized) {
            uiController.applyUiMode(mode)
        }
    }

    fun toggleDailyOverlay() {
        if (::uiController.isInitialized) {
            uiController.toggleDailyOverlay()
        }
    }

    fun hideDailyOverlay() {
        if (::uiController.isInitialized) {
            uiController.hideDailyOverlay()
        }
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        playerController.resume()
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        playerController.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        uiController.stopStatusUpdates()
        playerController.releasePlayer()
        errorRecoveryManager.release()
    }

    // SurfaceHolder.Callback 实现
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created, Surface有效: ${holder.surface?.isValid == true}")
        isSurfaceCreated = true
        playerController.setSurfaceHolder(holder)
        // 如果已经有播放地址，延迟一小段时间开始播放，确保Surface完全准备好
        tvViewModel?.getVideoUrlCurrent()?.let { url ->
            if (playerController.getPlayerState() == UnifiedVideoPlayer.PlayerState.IDLE) {
                Log.d(TAG, "Surface已创建，准备播放: $url")
                // 延迟100ms开始播放，确保Surface完全初始化
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // 需要重新创建监听器
                    val playerListener = object : PlayerController.PlayerEventListener {
                        override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                            Log.d(TAG, "播放器状态变化: $state")
                            when (state) {
                                UnifiedVideoPlayer.PlayerState.PLAYING -> {
                                    (activity as MainActivity).isPlaying()
                                }
                                UnifiedVideoPlayer.PlayerState.ERROR -> {
                                    tvViewModel?.changed()
                                }
                                else -> {}
                            }
                        }

                        override fun onError(error: String) {
                            Log.e(TAG, "播放错误: $error")
                            tvViewModel?.changed()
                            uiController.showErrorMessage(error)
                        }

                        override fun onBufferingUpdate(percent: Int) {}
                        override fun onProgressUpdate(position: Long, duration: Long) {}
                        override fun onPlaybackCompleted() {
                            tvViewModel?.changed()
                        }

                        override fun onPlaying() {
                            (activity as MainActivity).isPlaying()
                        }
                    }
                    playerController.initializePlayer(url, playerListener)
                }, 100)
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: $width x $height, Surface有效: ${holder.surface?.isValid == true}")
        // Surface尺寸变化时重新设置，确保视频输出正确
        playerController.setSurfaceHolder(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed")
        isSurfaceCreated = false
        playerController.pause()
    }

    /**
     * 获取当前播放器信息
     */
    fun getPlayerInfo(): String {
        return playerController.getPlayerInfo()
    }

    /**
     * 切换播放器（用于测试）
     */
    fun switchPlayer(forceType: UnifiedVideoPlayer.PlayerType) {
        playerController.switchPlayer(forceType)
        uiController.updatePlayerTypeSelection(forceType)
    }

    /**
     * 切换控制面板显示/隐藏
     */
    fun toggleControlPanel() {
        uiController.toggleControlPanel()
    }

    /**
     * 切换性能监控面板显示/隐藏
     */
    fun togglePerformanceMonitor() {
        uiController.togglePerformanceMonitor()
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}
