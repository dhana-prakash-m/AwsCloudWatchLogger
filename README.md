# AwsLogger

This library writes the received logs to the local file and streams the logs to the AWS CloudWatch at a regular interval(10 minutes) and also when the app is initialized.

## Setup

In your application class initialize the logger by passing in the application context. This must be the first call to AwsLogger.

```kotlin
// Pass in the application context
AwsLogger.init(context)
```
Unless you call this method with the required parameters to setup the client the logs that are written to the local file will not be streamed to cloudwatch. So once you have all the required parameters call the method to setup the client. The logs will be automatically started streaming at a regular interval to AWS cloudWatch once this method is called.

```kotlin
AwsLogger.setupLogClient(
    identityPoolId = "Your identity pool id",
    region = AwsRegions.AP_SOUTH_1,// You can choose any region
    groupName = "Your log group name",
    streamName = "Your log stream name",
)
```

Use this method to log. This method accepts a log message and optional varargs parameters of type String which will be appended to the end of the log message

```kotlin
AwsLogger.log(
    logMessage = "This is a debug log",
    logDetails = arrayOf("debug", "12/02/2023 12:04:23")
)
```
The above log will be displayed in the aws console as below
```Text
This is a debug log | debug | 12/02/2023 12:04:23
```

The logs will be automatically streamed to cloudWatch at a regular interval once the logger is initialized and client is setup. But if you require to stream the logs manually you can use the below method to do the work.
```kotlin
AwsLogger.uploadLogs()
```
