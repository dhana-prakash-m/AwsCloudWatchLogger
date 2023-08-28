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
    }

    /**
     * Property that contains the sequence token
     */
    var sequenceToken: String?
        get() = preference.getString(KEY_SEQUENCE_TOKEN, null)
        set(token) = editor.putString(KEY_SEQUENCE_TOKEN, token).apply()

    /**
     * To clear the logger preferences
     */
    fun resetPreferences() {
        with(editor) {
            remove(KEY_SEQUENCE_TOKEN)
            apply()
        }
    }
}