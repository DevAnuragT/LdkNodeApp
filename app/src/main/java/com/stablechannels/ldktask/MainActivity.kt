package com.stablechannels.ldktask

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.lightningdevkit.ldknode.Builder
import org.lightningdevkit.ldknode.Network
import org.lightningdevkit.ldknode.Node
import org.lightningdevkit.ldknode.EsploraSyncConfig
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var node: Node? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.nodeIdTextView)

        thread {
            try {
                // Set up the builder
                val storageDir = File(filesDir, "ldk-node").absolutePath
                val builder = Builder()
                builder.setNetwork(Network.SIGNET)
                builder.setChainSourceEsplora("https://mempool.space/signet/api", EsploraSyncConfig(300UL, 300UL, 300UL))
                builder.setStorageDirPath(storageDir)

                // Build and start the node
                node = builder.build()
                node?.start()

                val nodeId = node?.nodeId()

                // Update UI on the main thread
                runOnUiThread {
                    textView.text = "Node ID:\n$nodeId"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    textView.text = "Error initializing node:\n${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        thread {
            try {
                node?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
