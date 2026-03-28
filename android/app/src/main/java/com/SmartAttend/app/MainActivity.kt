package com.SmartAttend.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.SmartAttend.app.ui.SmartAttendApp
import com.SmartAttend.app.ui.theme.SmartAttendTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartAttendTheme {
                SmartAttendApp()
            }
        }
    }
}
