package com.gamaxtersnow.mytv

import android.content.Context
import android.content.SharedPreferences

object SP {
    // Name of the sp file TODO Should use a meaningful name and do migrations
    private const val SP_FILE_NAME = "MainActivity"

    // If Change channel with up and down in reversed order or not
    private const val KEY_CHANNEL_REVERSAL = "channel_reversal"

    // If use channel num to select channel or not
    private const val KEY_CHANNEL_NUM = "channel_num"

    private const val KEY_TIME = "time"

    // If start app on device boot or not
    private const val KEY_BOOT_STARTUP = "boot_startup"

    private const val KEY_GRID = "grid"

    // Position in list of the selected channel item
    private const val KEY_POSITION = "position"

    // guid
    private const val KEY_GUID = "guid"

    // Remote M3U8 playlist URL
    private const val KEY_REMOTE_URL = "remote_url"

    // Auto-update interval in milliseconds (default: 24 hours)
    private const val KEY_UPDATE_INTERVAL = "update_interval"

    // Last successful update timestamp
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val KEY_EPG_SOURCE = "epg_source"
    private const val KEY_EPG_LAST_UPDATE_TIME = "epg_last_update_time"
    private const val KEY_EPG_UPDATE_INTERVAL = "epg_update_interval"

    private lateinit var sp: SharedPreferences

    /**
     * The method must be invoked as early as possible(At least before using the keys)
     */
    fun init(context: Context) {
        sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
    }

    var channelReversal: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_REVERSAL, false)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_REVERSAL, value).apply()

    var channelNum: Boolean
        get() = sp.getBoolean(KEY_CHANNEL_NUM, true)
        set(value) = sp.edit().putBoolean(KEY_CHANNEL_NUM, value).apply()

    var time: Boolean
        get() = sp.getBoolean(KEY_TIME, true)
        set(value) = sp.edit().putBoolean(KEY_TIME, value).apply()

    var bootStartup: Boolean
        get() = sp.getBoolean(KEY_BOOT_STARTUP, false)
        set(value) = sp.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()

    var grid: Boolean
        get() = sp.getBoolean(KEY_GRID, false)
        set(value) = sp.edit().putBoolean(KEY_GRID, value).apply()

    var itemPosition: Int
        get() = sp.getInt(KEY_POSITION, 0)
        set(value) = sp.edit().putInt(KEY_POSITION, value).apply()

    var guid: String
        get() = sp.getString(KEY_GUID, "") ?: ""
        set(value) = sp.edit().putString(KEY_GUID, value).apply()

    var remoteUrl: String
        get() = sp.getString(KEY_REMOTE_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_REMOTE_URL, value).apply()

    var updateInterval: Long
        get() = sp.getLong(KEY_UPDATE_INTERVAL, 24 * 60 * 60 * 1000L)
        set(value) = sp.edit().putLong(KEY_UPDATE_INTERVAL, value).apply()

    var lastUpdateTime: Long
        get() = sp.getLong(KEY_LAST_UPDATE_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_UPDATE_TIME, value).apply()

    var epgSource: String
        get() = sp.getString(KEY_EPG_SOURCE, "") ?: ""
        set(value) = sp.edit().putString(KEY_EPG_SOURCE, value).apply()

    var epgLastUpdateTime: Long
        get() = sp.getLong(KEY_EPG_LAST_UPDATE_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_EPG_LAST_UPDATE_TIME, value).apply()

    var epgUpdateInterval: Long
        get() = sp.getLong(KEY_EPG_UPDATE_INTERVAL, 12 * 60 * 60 * 1000L)
        set(value) = sp.edit().putLong(KEY_EPG_UPDATE_INTERVAL, value).apply()
}
