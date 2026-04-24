package com.lizongying.mytv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.MediaPlayer.Event
import org.videolan.libvlc.interfaces.IMedia

/**
 * VLC测试Activity，用于验证VLC的RTP播放功能
 * 这个Activity提供一个简单的界面来测试RTP流播放
 */
class VLCTestActivity : AppCompatActivity() {
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var urlInput: EditText
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    
    companion object {
        private const val TAG = "VLCTestActivity"
        private const val DEFAULT_RTP_URL = "rtp://239.1.1.1:5000"
    }
    
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建简单的垂直布局
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        
        // 标题
        val title = TextView(this)
        title.text = "VLC RTP播放测试"
        title.textSize = 24f
        layout.addView(title)
        
        // URL输入
        val urlLabel = TextView(this)
        urlLabel.text = "RTP URL:"
        urlLabel.textSize = 16f
        layout.addView(urlLabel)
        
        urlInput = EditText(this)
        urlInput.setText(DEFAULT_RTP_URL)
        urlInput.textSize = 14f
        layout.addView(urlInput)
        
        // 按钮容器
        val buttonLayout = LinearLayout(this)
        buttonLayout.orientation = LinearLayout.HORIZONTAL
        
        playButton = Button(this)
        playButton.text = "播放"
        playButton.setOnClickListener { playStream() }
        buttonLayout.addView(playButton)
        
        stopButton = Button(this)
        stopButton.text = "停止"
        stopButton.setOnClickListener { stopStream() }
        buttonLayout.addView(stopButton)
        
        layout.addView(buttonLayout)
        
        // 状态显示
        statusText = TextView(this)
        statusText.text = "状态: 未初始化"
        statusText.textSize = 14f
        layout.addView(statusText)
        
        // 日志显示
        logText = TextView(this)
        logText.text = "日志:\n"
        logText.textSize = 12f
        logText.setLines(10)
        layout.addView(logText)
        
        // SurfaceView用于视频渲染
        surfaceView = SurfaceView(this)
        surfaceView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            400
        )
        layout.addView(surfaceView)
        
        setContentView(layout)
        
        // 初始化VLC
        initializeVLC()
    }
    
    private fun initializeVLC() {
        try {
            val options = arrayListOf(
                "--aout=opensles",
                "--audio-time-stretch",
                "--network-caching=1500",
                "--rtsp-tcp",
                "--live-caching=1500"
            )
            
            libVLC = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVLC)
            
            // 设置事件监听器
            mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    when (event.type) {
                        Event.Opening -> {
                            log("媒体正在打开...")
                            updateStatus("正在打开媒体")
                        }
                        Event.Buffering -> {
                            val percent = event.buffering
                            log("缓冲中: $percent%")
                            updateStatus("缓冲中: $percent%")
                        }
                        Event.Playing -> {
                            log("开始播放")
                            updateStatus("播放中")
                        }
                        Event.Paused -> {
                            log("播放暂停")
                            updateStatus("已暂停")
                        }
                        Event.Stopped -> {
                            log("播放停止")
                            updateStatus("已停止")
                        }
                        Event.EndReached -> {
                            log("播放结束")
                            updateStatus("播放结束")
                        }
                        Event.EncounteredError -> {
                            log("播放错误: ${event.type}")
                            updateStatus("播放错误")
                        }
                        Event.TimeChanged -> {
                            // 时间更新，可以忽略
                        }
                        Event.PositionChanged -> {
                            // 位置更新，可以忽略
                        }
                        Event.SeekableChanged -> {
                            log("可跳转状态改变: ${event.seekable}")
                        }
                        Event.PausableChanged -> {
                            log("可暂停状态改变: ${event.pausable}")
                        }
                        else -> {
                            log("其他事件: ${event.type}")
                        }
                    }
                }
            })
            
            // 设置视频输出
            val holder = surfaceView.holder
            try {
                // 使用@Suppress来抑制类型不匹配警告
                @Suppress("TYPE_MISMATCH")
                val result = mediaPlayer.attachViews(holder, null, false, false)
                log("设置视频输出结果: $result")
            } catch (e: Exception) {
                Log.e(TAG, "设置视频输出失败: ${e.message}")
                // 在某些情况下，VLC可能会自动处理Surface
            }
            
            log("VLC初始化成功")
            updateStatus("VLC已初始化")
            
        } catch (e: Exception) {
            log("VLC初始化失败: ${e.message}")
            updateStatus("VLC初始化失败")
            e.printStackTrace()
        }
    }
    
    private fun playStream() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            log("URL不能为空")
            return
        }
        
        log("开始播放: $url")
        updateStatus("正在准备播放...")
        
        try {
            // 创建媒体
            val media = Media(libVLC, url)
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1500")
            media.addOption(":live-caching=1500")
            
            // 设置媒体并播放
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            
        } catch (e: Exception) {
            log("播放失败: ${e.message}")
            updateStatus("播放失败")
            e.printStackTrace()
        }
    }
    
    private fun stopStream() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                log("停止播放")
                updateStatus("已停止")
            }
        } catch (e: Exception) {
            log("停止失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun log(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            val currentText = logText.text.toString()
            logText.text = "$currentText$message\n"
        }
    }
    
    private fun updateStatus(status: String) {
        runOnUiThread {
            statusText.text = "状态: $status"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::mediaPlayer.isInitialized) {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
            if (::libVLC.isInitialized) {
                libVLC.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
}