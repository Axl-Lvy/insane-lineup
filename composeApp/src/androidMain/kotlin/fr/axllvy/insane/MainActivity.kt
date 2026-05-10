package fr.axllvy.insane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.axllvy.insane.notifications.AndroidPermissionRequester

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidPermissionRequester.attach(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onDestroy() {
        AndroidPermissionRequester.detach()
        super.onDestroy()
    }
}
