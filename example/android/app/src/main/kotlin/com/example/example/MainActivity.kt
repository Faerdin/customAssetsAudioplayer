package com.example.example

//import io.flutter.embedding.android.FlutterActivity

//class MainActivity : FlutterActivity()

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You can try setting a simple layout if you have one, or just leave it empty
        // R.layout.activity_main might not exist or might be Flutter specific
        // For this test, just having it compile is enough.
        // setContentView(R.layout.activity_main)
    }
}