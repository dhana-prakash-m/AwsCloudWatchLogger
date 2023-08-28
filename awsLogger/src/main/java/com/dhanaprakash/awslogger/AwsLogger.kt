package com.dhanaprakash.awslogger

import android.content.Context
import android.util.Log
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.logs.AmazonCloudWatchLogsClient
import com.amazonaws.services.logs.model.CreateLogGroupRequest
import com.amazonaws.services.logs.model.CreateLogStreamRequest
import com.amazonaws.services.logs.model.DataAlreadyAcceptedException
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest
import com.amazonaws.services.logs.model.InputLogEvent
import com.amazonaws.services.logs.model.InvalidParameterException
import com.amazonaws.services.logs.model.InvalidSequenceTokenException
import com.amazonaws.services.logs.model.PutLogEventsRequest
import com.amazonaws.services.logs.model.ResourceNotFoundException
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

/**
 * The class that writes the received to the local file immediately and streams the logs from the local
 * file to AWS CloudWatch at a regular interval.
 */
object AwsLogger {
    private const val logFileName = "AwsCloudWatchLogs.txt"
    private const val tempLogFileName = "AwsCloudWatchLogsTemp.txt"
    private const val logSeparator = " | "
    private const val batchSize = 5000
    private val TAG: String = this::class.java.name
    private val gson = Gson()
    private var totalLogEvents: MutableList<InputLogEvent> = mutableListOf()
    private var client: AmazonCloudWatchLogsClient? = null
    private lateinit var appContext: Context
    private lateinit var preferences: AwsLoggerPreferences
    private val ioDispatcher = Dispatchers.IO
    private const val INITIAL_LOG_UPLOAD_DELAY: Long = 1000 * 60 * 1 // 1 Minute
    private const val REPETITIVE_LOG_UPLOAD_DELAY: Long = 1000 * 60 * 10 // 10 Minutes

    /**
     * The io dispatcher that limits the parallelism to the 1. That is no more than one coroutine will
     * be executed in this dispatcher at the same time. This is used for writing logs to local file
     * in sequential manner, because the order of timestamp in the logs should be in a correct sequence.
     * So whatever the thread the log method can be called but we write them sequentially to the file.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val limitedIoDispatcher = ioDispatcher.limitedParallelism(1)
    private val sequentialExecutionScope = CoroutineScope(SupervisorJob() + limitedIoDispatcher)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var throwableCallback: ((Throwable) -> Unit)? = null
    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable -> throwableCallback?.invoke(throwable) }
    private var logGroupName: String? = null
    private var logStreamName: String? = null

    /**
     * Initializes the logger
     *
     * @param context           The application context
     * @param throwableCallback Callback to let the consumers know the exceptions occurred here
     */
    fun init(context: Context, throwableCallback: ((Throwable) -> Unit)? = null) {
        appContext = context.applicationContext
        this.throwableCallback = throwableCallback
        preferences = AwsLoggerPreferences(context)
        applicationScope.launch(coroutineExceptionHandler) {
            delay(INITIAL_LOG_UPLOAD_DELAY)
            uploadLogsWork()
        }
    }

    /**
     * Streams the logs to cloudWatch at a regular interval
     */
    private suspend fun uploadLogsWork() {
        uploadLogs()
        delay(REPETITIVE_LOG_UPLOAD_DELAY)
        uploadLogsWork()
    }

    /**
     * Configures the AWS CloudWatchLogs client
     *
     * @param identityPoolId The identity pool id
     * @param region         The AWS region which you can get from [AwsRegions]
     * @param groupName      The log group name
     * @param streamName     The log stream name
     */
    fun setupLogClient(
        identityPoolId: String,
        region: AwsRegions,
        groupName: String,
        streamName: String,
    ) {
        applicationScope.launch(coroutineExceptionHandler) {
            logGroupName = groupName
            logStreamName = streamName
            client = AmazonCloudWatchLogsClient(
                CognitoCachingCredentialsProvider(
                    appContext,
                    identityPoolId,
                    region.awsRegion
                )
            ).apply {
                setRegion(Region.getRegion(region.awsRegion))
            }
            if (!checkLogGroupExists(groupName)) createLogGroup(groupName)
            if (!checkLogStreamExists(groupName, streamName)) createLogStream(groupName, streamName)
        }
    }

    /**
     * To check whether the log group with the given name is already present
     *
     * @param groupName The log group name to check
     * @return True if exists else false
     */
    private suspend fun checkLogGroupExists(groupName: String): Boolean =
        withContext(ioDispatcher) {
            val request = DescribeLogGroupsRequest().withLogGroupNamePrefix(groupName)
            val result = client?.describeLogGroups(request)
            !result?.logGroups.isNullOrEmpty()
        }

    /**
     * To check whether the log stream with the given name is already present
     *
     * @param groupName  The log group name to check
     * @param streamName The log stream name to check
     * @return True if exists else false
     */
    private suspend fun checkLogStreamExists(groupName: String, streamName: String): Boolean =
        withContext(ioDispatcher) {
            val request = DescribeLogStreamsRequest(groupName).withLogStreamNamePrefix(streamName)
            val result = client?.describeLogStreams(request)
            !result?.logStreams.isNullOrEmpty()
        }

    /**
     *  Creates the log group with the given name
     *
     * @param logGroupName The log group name
     */
    private suspend fun createLogGroup(logGroupName: String): Unit = withContext(ioDispatcher) {
        with(CreateLogGroupRequest()) {
            this.logGroupName = logGroupName
            try {
                client?.createLogGroup(this)
            } catch (exception: Exception) {
                throwableCallback?.invoke(exception)
                Log.e(TAG, "createLogGroup failed", exception)
            }
        }
    }

    /**
     * Creates the log stream with the given name
     *
     * @param logGroupName  The log group name
     * @param logStreamName The log stream name
     */
    private suspend fun createLogStream(logGroupName: String, logStreamName: String): Unit =
        withContext(ioDispatcher) {
            with(CreateLogStreamRequest()) {
                this.logGroupName = logGroupName
                this.logStreamName = logStreamName
                try {
                    client?.createLogStream(this)
                } catch (exception: Exception) {
                    throwableCallback?.invoke(exception)
                }
            }
        }

    /**
     * Logs the given message to the local text file
     *
     * @param logMessage The log message
     * @param logDetails The optional log details
     */
    fun log(
        logMessage: String,
        vararg logDetails: String?,
    ) {
        val logMessageBuilder = StringBuilder()
        logDetails.forEach {
            if (!it.isNullOrBlank()) logMessageBuilder.append(it).append(logSeparator)
        }
        logMessageBuilder.append(logMessage)
        val completeMessage = InputLogEvent().apply {
            message = logMessageBuilder.toString()
            timestamp = System.currentTimeMillis()
        }
        // This writes logs to the file sequentially since it uses a coroutine dispatcher with limited parallelism
        sequentialExecutionScope.launch(coroutineExceptionHandler) {
            cacheLogInLocalStorage(gson.toJson(completeMessage))
        }
    }

    /**
     * To cache the logs in the local storage as a text file
     *
     * @param message The log message
     */
    private fun cacheLogInLocalStorage(message: String?) {
        val file = File(appContext.filesDir, logFileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        FileWriter(file, true).use {
            it.appendLine(message)
        }
    }

    /**
     * To upload the logs to the AWS cloudWatch
     */
    suspend fun uploadLogs() {
        if (client == null) return
        sequentialExecutionScope.launch(coroutineExceptionHandler) {
            val bufferedReader = File(appContext.filesDir, logFileName).bufferedReader()
            // Here we are converting the json into corresponding object and appending it a list of log events
            val events = bufferedReader
                .readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { gson.fromJson(it, InputLogEvent::class.java) }
                .filter { it.timestamp != null && it.message != null }
                .toMutableList()
            if (events.isEmpty()) return@launch
            totalLogEvents.clear()
            totalLogEvents.addAll(events)
            splitIntoBatchesAndUpload(events)
        }
    }

    /**
     * To split all the logs into batches based on certain time and count constraints
     *
     * @param events  The log events
     * @param batches The resulting batches
     */
    private suspend fun splitIntoBatchesAndUpload(
        events: MutableList<InputLogEvent>,
        batches: MutableList<List<InputLogEvent>> = mutableListOf(),
    ) {
        val startTimeOfBatch = events.firstOrNull()?.timestamp ?: return
        // The logs in a batch can not be more than 24 hours interval, so we are splitting the batches
        //in 24 hours interval
        val endTimeOfBatch = startTimeOfBatch.plus(TimeUnit.DAYS.toMillis(1))
        val batchesWithExpectedTimeInterval = events.groupBy {
            it.timestamp <= endTimeOfBatch
        }[true]
        batchesWithExpectedTimeInterval?.let { logEvents ->
            // A log batch size should not have more than 10000 logs, so we are splitting the batches
            // with a maximum size of 5000
            logEvents.chunked(batchSize).forEach {
                batches.add(it)
            }
            events.removeAll(logEvents)
        }
        if (events.isNotEmpty()) splitIntoBatchesAndUpload(events, batches)
        uploadBatchesToAws(batches)
    }

    /**
     * To upload log batches to aws
     *
     * @param batches The log batches
     */
    private suspend fun uploadBatchesToAws(batches: MutableList<List<InputLogEvent>>) {
        for (batch in batches) {
            try {
                uploadLogsToAws(batch)
            } catch (exception: Exception) {
                when (exception) {
                    // This exception occurs when we have given a wrong sequence token in the putLogEvents
                    // request. This exception returns a expected sequence token that needs to be sent
                    // in the next putLogEvents request. So here we are retrying with the expected sequence token.
                    is InvalidSequenceTokenException -> {
                        preferences.sequenceToken = exception.expectedSequenceToken
                        uploadLogsToAws(batch)
                    }
                    // This exception occurs when we try to upload the same log event twice. So here
                    // we are removing the logs that are already uploaded and saving the expected
                    // sequence token for next putLogEvents request.
                    is DataAlreadyAcceptedException -> {
                        preferences.sequenceToken = exception.expectedSequenceToken
                        deleteUploadedLogs(batch)
                    }
                    // This exception occurs when we try to upload logs without creating log group or
                    // log stream or with wrong name for each, so if this exception occur we will retry
                    // creating log stream and log group, so that upload logs can succeed when called next time
                    is ResourceNotFoundException -> {
                        logGroupName?.let { groupName ->
                            createLogGroup(groupName)
                            logStreamName?.let {
                                createLogStream(
                                    logGroupName = groupName,
                                    logStreamName = it,
                                )
                            }
                        }
                        return
                    }
                    // This exception occurs if we order the logs with timestamp with wrong order,
                    // so we will update all the logs with the same timestamp and upload it again
                    is InvalidParameterException -> {
                        changeTimestampAndUploadLogs()
                    }

                    else -> {
                        throwableCallback?.invoke(exception)
                        Log.e(this.javaClass.name, "uploadLogsToAws failed", exception)
                    }
                }
            }
        }
    }

    /**
     * To upload logs to aws
     *
     * @param logs The logs
     */
    private fun uploadLogsToAws(logs: List<InputLogEvent>) {
        val request = PutLogEventsRequest()
        request.apply {
            setLogEvents(logs)
            logGroupName = this@AwsLogger.logGroupName
            logStreamName = this@AwsLogger.logStreamName
            val token = preferences.sequenceToken
            if (token != null) sequenceToken = token
        }
        val result = client?.putLogEvents(request)
        //The successful result contains the next sequence token which need to be sent in the next putLogEvents request
        result?.nextSequenceToken?.let {
            preferences.sequenceToken = it
            //Here we are deleting the log file as it was uploaded
            deleteUploadedLogs(logs)
        }
    }

    /**
     * To order the logs with the latest timestamp in chronological order and upload log
     */
    private suspend fun changeTimestampAndUploadLogs() {
        val currentTimeStamp = System.currentTimeMillis()
        totalLogEvents.forEach { it.timestamp = currentTimeStamp }
        splitIntoBatchesAndUpload(totalLogEvents)
    }

    /**
     * To delete the uploaded logs
     *
     * @param uploadedLogs The uploaded logs
     */
    private fun deleteUploadedLogs(uploadedLogs: List<InputLogEvent>) {
        totalLogEvents.removeAll(uploadedLogs)
        //Create temp file to store logs that are not uploaded
        val file = File(appContext.filesDir, tempLogFileName)
        if (!file.exists()) {
            // Ignore the warning as we are already using IO Dispatcher
            file.createNewFile()
        }
        // Here we are using buffered writer since the number of line is large, if lines is less we could
        // have used fileWriter
        file.bufferedWriter().use { writer ->
            totalLogEvents.forEach {
                writer.appendLine(gson.toJson(it))
            }
        }
        //Delete the log file which include all logs including the uploaded ones
        File(appContext.filesDir, logFileName).delete()
        //Rename the temp file that includes only logs that are not uploaded with the deleted log file name
        file.renameTo(File(appContext.filesDir, logFileName))
    }

    /**
     * To clear the logger preferences such as log stream name, log group name and identity pool id
     */
    fun resetConfiguration() = preferences.resetPreferences()

    /**
     * To clear all the cached logs from the local file
     */
    fun flushLogs() {
        sequentialExecutionScope.launch(coroutineExceptionHandler) {
            File(appContext.filesDir, logFileName).delete()
        }
    }
}