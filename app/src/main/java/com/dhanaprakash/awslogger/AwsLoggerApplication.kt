package com.dhanaprakash.awslogger

import android.app.Application

class AwsLoggerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AwsLogger.init(this)
        AwsLogger.setupLogClient(
            identityPoolId = "Your identity pool id",
            region = AwsRegions.AP_SOUTH_1,// Choose any region
            groupName = "Your log group name",
            streamName = "Your log stream name",
        )
        AwsLogger.log(
            logMessage = "This is a debug log",
            logDetails = arrayOf("debug", "12/02/2023 12:04:23")
        )
    }
}