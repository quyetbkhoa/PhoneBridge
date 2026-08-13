package com.quyetbkhoa.phonebridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.quyetbkhoa.phonebridge.ui.MainViewModel
import com.quyetbkhoa.phonebridge.ui.PhoneBridgeRoot
import com.quyetbkhoa.phonebridge.ui.theme.PhoneBridgeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModelHandleUsbIntent(intent)
        setContent {
            PhoneBridgeTheme {
                PhoneBridgeRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelHandleUsbIntent(intent)
    }

    private fun viewModelHandleUsbIntent(intent: Intent?) {
        (application as PhoneBridgeApplication).graph.usbController.handleIntent(intent)
    }
}
