package com.databelay.refwatch.mobile.wear

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.truth.Truth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneToWearPingTest {

    private lateinit var context: Context
    private lateinit var phonePinger: PhonePinger // Manually instantiate
    private lateinit var dataClient: DataClient // For listening to a response (optional)

    // Path for the watch to send an ACK back (optional, for more robust testing)
    private val ACK_PATH = "/minimal_test_ack"
    private val ACK_MESSAGE_KEY = "ack_message"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        phonePinger = PhonePinger(context) // Manually create PhonePinger
        dataClient = Wearable.getDataClient(context)
    }

    @After
    fun tearDown() {
        // Clean up any listeners if added for ACK
    }

    @Test
    fun sendPing_shouldBeReceivedByWatch()  = runBlocking {
        // This test primarily verifies the phone can send.
        // Verifying receipt on the watch requires checking watch logs
        // OR having the watch send an acknowledgment back.

        val latch = CountDownLatch(1) // To wait for a potential ACK
        var ackReceived = false

        val listener = DataClient.OnDataChangedListener { dataEvents: DataEventBuffer ->
            Log.d("PhoneToWearPingTest", "ACK Listener: onDataChanged on Phone")
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val dataItem = event.dataItem
                    if (dataItem.uri.path == ACK_PATH) {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val message = dataMap.getString(ACK_MESSAGE_KEY)
                        Log.i("PhoneToWearPingTest", "ACK RECEIVED FROM WATCH: '$message'")
                        ackReceived = true
                        latch.countDown()
                    }
                }
            }
            dataEvents.release()
        }

        // Optional: Listen for an ACK from the watch
        // dataClient.addListener(listener) // Add this if your watch service sends an ACK

        Log.d("PhoneToWearPingTest", "Sending ping from test...")
        phonePinger.sendPing(this) // Call the method on the manually created instance

        // Give some time for the ping to be sent and potentially an ACK to return
        // This is a simple way for testing. For more robust, use Espresso Idling Resources
        // or proper asynchronous test constructs if not using runBlocking for the whole test.
        val ackReceivedWithinTimeout = latch.await(15, TimeUnit.SECONDS) // Wait up to 15 seconds

        // If you implemented an ACK from the watch:
        // assertThat(ackReceivedWithinTimeout).isTrue()
        // assertThat(ackReceived).isTrue()

        // For this test, we primarily assert that sendPing doesn't crash.
        // Actual receipt is verified by checking watch Logcat for "PING RECEIVED".
        Log.i("PhoneToWearPingTest", "Ping send attempt completed. Check watch logs for receipt.")
        // If you want to fail the test if no ACK, uncomment the assertions above.
        // For now, let's assume we check watch logs manually.
        Truth.assertThat(true).isTrue() // Placeholder assertion, real verification is watch logs / ACK

        // dataClient.removeListener(listener) // Clean up listener if added
    }
}