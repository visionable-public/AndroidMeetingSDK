package com.visionable.meetingrefapp

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.visionable.meetingsdk.MeetingSDK
import com.visionable.meetingrefapp.databinding.MainActivityBinding
import com.visionable.meetingrefapp.fragments.MeetingFragment
import com.visionable.meetingrefapp.fragments.SetUpFragment

/**
 * Main Activity class that holds:
 * 1. Some of the relevant MeetingSDK calls
 * 2. All of the Fragment transitions
 */
class MainActivity : AppCompatActivity(), SdkListener {

    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide app title

        // Initializes the Visionable MeetingSDK that is bound to your application context
        MeetingSDK.initializeSDK(this)

        // Navigate to [SetUpFragment]
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<SetUpFragment>(R.id.container)
            }
        }
    }

    /**
     * Joins the initialized meeting with the set participant name
     * Also sets `this@MainActivity` as the INotificationCallback for the MeetingSDK
     *
     * @param participantName - Display name for the participant joining
     */
    override fun joinMeeting(participantName: String) {
        // Initialize MeetingFragment in order to set it as the MeetingSDK notification callback
        val meetingFragment = MeetingFragment()
        val setupFragment = supportFragmentManager.findFragmentById(R.id.container)

        // Add the fragment in a hidden state in order to not disrupt UI
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(R.id.container, meetingFragment)
            hide(meetingFragment)
        }

        MeetingSDK.setNotificationCallback(meetingFragment)
        val joinMeetingStatus = MeetingSDK.joinMeeting(participantName)

        // If Join Meeting succeeds
        if (joinMeetingStatus == 1) {
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