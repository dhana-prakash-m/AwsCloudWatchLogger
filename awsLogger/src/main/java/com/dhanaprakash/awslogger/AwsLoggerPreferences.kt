package com.dhanaprakash.awslogger

import android.content.Context
import android.content.SharedPreferences

/**
 * The class to maintain the shared preferences for cloud watch logger
 */
class AwsLoggerPreferences constructor(context: Context) {
    private val preference: SharedPreferences =
        context.getSharedPreferences("AwsCloudWatchLogger", Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = preference.edit()

    companion object {
        private const val KEY_SEQUENCE_TOKEN = "sequence_token"
        private const val KEY_GROUP_NAME = "group_name"
        private const val KEY_STREAM_NAME = "stream_name"
        private const val KEY_IDENTITY_POOL_ID = "identity_pool_id"
    }

    /**
     * Property that contains the sequence token
     */
    var sequenceToken: String?
        get() = preference.getString(KEY_SEQUENCE_TOKEN, null)
        set(token) = editor.putString(KEY_SEQUENCE_TOKEN, token).apply()

    /**
     * Property that contains the log group name
     */
    var groupName: String?
        get() = preference.getString(KEY_GROUP_NAME, "default_log_group")
        set(name) = editor.putString(KEY_GROUP_NAME, name).apply()

    /**
     * Property that contains the log stream name
     */
    var streamName: String?
        get() = preference.getString(KEY_STREAM_NAME, "default_log_stream")
        set(name) = editor.putString(KEY_STREAM_NAME, name).apply()

    /**
     * Property that contains the app version
     */
    var identityPoolId: String?
        get() = preference.getString(KEY_IDENTITY_POOL_ID, null)
        set(id) = editor.putString(KEY_IDENTITY_POOL_ID, id).apply()

    /**
     * To clear the logger preferences
     */
    fun resetPreferences() {
        with(editor) {
            remove(KEY_SEQUENCE_TOKEN)
            remove(KEY_GROUP_NAME)
            remove(KEY_STREAM_NAME)
            remove(KEY_IDENTITY_POOL_ID)
            editor.apply()
        }
    }
}