package com.visionable.meetingrefapp

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Environment.DIRECTORY_DOCUMENTS
import android.os.Environment.getExternalStoragePublicDirectory
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.visionable.meetingrefapp.databinding.MainActivityBinding
import com.visionable.meetingrefapp.fragments.MeetingFragment
import com.visionable.meetingrefapp.fragments.SetUpFragment
import com.visionable.meetingsdk.MeetingSDK

import org.json.JSONObject
/**
 * Main Activity class that holds:
 * 1. Some of the relevant MeetingSDK calls
 * 2. All of the Fragment transitions
 */
class MainActivity : AppCompatActivity(), SdkListener {

    private lateinit var binding: MainActivityBinding
    private lateinit var audioMgr: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide app title

        // Initializes the Visionable MeetingSDK that is bound to your application context
        MeetingSDK.initializeSDK(this)
        Log.d("RONSTAG",MeetingSDK.getTimeZone())

        // Navigate to [SetUpFragment]
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<SetUpFragment>(R.id.container)
            }
        }

        ActivityCompat.requestPermissions(/* activity = */ this, /* permissions = */
            arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE),23);

        val externalDirectory = getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)
        val loggingDir = externalDirectory?.absolutePath

        MeetingSDK.setLogDirectory(loggingDir + "/visionable")
        MeetingSDK.enableCombinedLogs(true)
        MeetingSDK.enableLogForwarding(false)

        val result = MeetingSDK.deleteAllLogFiles()

        MeetingSDK.enableActiveLogging("RefAppLog")

        audioMgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioMgr.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioMgr.isSpeakerphoneOn = true;

        MeetingSDK.setTraceLevel(MeetingSDK.DebugLevel.DBG3)
    }

    /**
     * Joins the initialized meeting with the set participant name
     * Also sets `this@MainActivity` as the INotificationCallback for the MeetingSDK
     *
     * @param participantName - Display name for the participant joining
     */
    override fun joinMeeting(server: String, meetingUUID: String, key: String?, participantName: String) {
        // Initialize MeetingFragment in order to set it as the MeetingSDK notification callback
        val meetingFragment = MeetingFragment()
        val setupFragment = supportFragmentManager.findFragmentById(R.id.container)

        // Add the fragment in a hidden state in order to not disrupt UI
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(R.id.container, meetingFragment)
            hide(meetingFragment)
        }

        //MeetingSDK.setDelegate(null, Looper.getMainLooper())
        MeetingSDK.joinMeeting(server,meetingUUID,key,"", participantName) { joined ->
            if (joined) {
                // Remove SetUpFragment
                setupFragment?.let { fragment ->
                    supportFragmentManager.commit {
                        show(meetingFragment)
                        remove(fragment)
                    }
                }
            } else {
                // Remove MeetingFragment instance
                supportFragmentManager.commit { remove(meetingFragment) }
                showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }
    }

    override fun joinMeetingWithToken(server: String, meetingUUID: String, token: String?, participantName: String) {
        // Initialize MeetingFragment in order to set it as the MeetingSDK notification callback
        val meetingFragment = MeetingFragment()
        val setupFragment = supportFragmentManager.findFragmentById(R.id.container)

        // Add the fragment in a hidden state in order to not disrupt UI
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(R.id.container, meetingFragment)
            hide(meetingFragment)
        }

        //MeetingSDK.setDelegate(null, Looper.getMainLooper())

        MeetingSDK.joinMeetingWithToken(server,meetingUUID,token,/*userUUID*/"",participantName) { joined ->
            if (joined) {
                // Remove SetUpFragment
                setupFragment?.let { fragment ->
                    supportFragmentManager.commit {
                        show(meetingFragment)
                        remove(fragment)
                    }
                }
            } else {
                // Remove MeetingFragment instance
                supportFragmentManager.commit { remove(meetingFragment) }
                showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }
    }

    override fun joinMeetingWithTokenAndJWT(server: String, meetingUUID: String, token: String, jwt: String, participantName: String) {
        // Initialize MeetingFragment in order to set it as the MeetingSDK notification callback
        val meetingFragment = MeetingFragment()
        val setupFragment = supportFragmentManager.findFragmentById(R.id.container)

        // Add the fragment in a hidden state in order to not disrupt UI
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(R.id.container, meetingFragment)
            hide(meetingFragment)
        }

        //MeetingSDK.setDelegate(null, Looper.getMainLooper())

        MeetingSDK.joinMeetingWithTokenAndJWT(server,meetingUUID,token,jwt,participantName) { joined ->
            if (joined) {
                // Remove SetUpFragment
                setupFragment?.let { fragment ->
                    supportFragmentManager.commit {
                        show(meetingFragment)
                        remove(fragment)
                    }
                }
            } else {
                // Remove MeetingFragment instance
                supportFragmentManager.commit { remove(meetingFragment) }
                showErrorModal(
                    title = R.string.join_meeting_alert_title,
                    message = R.string.init_meeting_alert_message
                )
            }
        }

    }

    /**
     * Helper Function
     * Displays alert dialog when user is unable to initialize meeting
     *
     * @param title
     * @param message
     */
    override fun showErrorModal(title: Int?, message: Int?) {
        AlertDialog.Builder(this).apply {
            title?.let { setTitle(it) }
            message?.let { setMessage(it) }
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                // User clicked OK button
                dialog.dismiss()
            }

            create()
            show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MeetingSDK.destroySDK()
    }
}
