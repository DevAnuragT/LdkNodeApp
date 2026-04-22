package com.stablechannels.ldktask

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lightningdevkit.ldknode.Builder
import org.lightningdevkit.ldknode.EsploraSyncConfig
import org.lightningdevkit.ldknode.Network
import org.lightningdevkit.ldknode.Node
import java.io.File

class MainActivity : AppCompatActivity() {

    private var node: Node? = null

    // UI Elements
    private lateinit var nodeIdText: TextView
    private lateinit var nodeStatusText: TextView
    private lateinit var addressText: TextView
    private lateinit var balanceText: TextView
    private lateinit var peersListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        nodeIdText = findViewById(R.id.nodeIdText)
        nodeStatusText = findViewById(R.id.nodeStatusText)
        addressText = findViewById(R.id.addressText)
        balanceText = findViewById(R.id.balanceText)
        peersListText = findViewById(R.id.peersListText)

        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)
        val btnGenerateAddress: Button = findViewById(R.id.btnGenerateAddress)
        val btnRefreshBalance: Button = findViewById(R.id.btnRefreshBalance)
        val btnConnectPeer: Button = findViewById(R.id.btnConnectPeer)
        val btnListPeers: Button = findViewById(R.id.btnListPeers)
        val btnOpenChannel: Button = findViewById(R.id.btnOpenChannel)

        val peerInput: EditText = findViewById(R.id.peerInput)
        val channelPubkeyInput: EditText = findViewById(R.id.channelPubkeyInput)
        val channelHostInput: EditText = findViewById(R.id.channelHostInput)
        val channelAmountInput: EditText = findViewById(R.id.channelAmountInput)

        // Setup Node
        initNode()

        // Tier 1: Start / Stop
        btnStart.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    node?.start()
                    updateStatus(true)
                } catch (e: Exception) {
                    showError("Failed to start node: ${e.message}")
                }
            }
        }

        btnStop.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    node?.stop()
                    updateStatus(false)
                } catch (e: Exception) {
                    showError("Failed to stop node: ${e.message}")
                }
            }
        }

        // Tier 1: Funding Address
        btnGenerateAddress.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val address = node?.onchainPayment()?.newAddress()
                    withContext(Dispatchers.Main) {
                        if (address != null) {
                            addressText.text = address
                            copyToClipboard("Funding Address", address)
                        } else {
                            Toast.makeText(this@MainActivity, "Node not initialized", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    showError("Failed to generate address: ${e.message}")
                }
            }
        }

        // Tier 1: Balance
        btnRefreshBalance.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val balances = node?.listBalances()
                    withContext(Dispatchers.Main) {
                        if (balances != null) {
                            val spendable = balances.spendableOnchainBalanceSats
                            val total = balances.totalOnchainBalanceSats
                            balanceText.text = "Spendable: $spendable sats\nTotal: $total sats"
                        } else {
                            Toast.makeText(this@MainActivity, "Node not initialized", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    showError("Failed to get balance: ${e.message}")
                }
            }
        }

        // Tier 2: Connect Peer
        btnConnectPeer.setOnClickListener {
            val uri = peerInput.text.toString().trim()
            if (uri.isEmpty() || !uri.contains("@")) {
                Toast.makeText(this, "Invalid format. Use pubkey@host:port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val parts = uri.split("@")
            val pubkey = parts[0]
            val address = parts[1]

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    node?.connect(pubkey, address, true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Connected to peer!", Toast.LENGTH_SHORT).show()
                        peerInput.text.clear()
                    }
                } catch (e: Exception) {
                    showError("Failed to connect peer: ${e.message}")
                }
            }
        }

        // Tier 2: List Peers
        btnListPeers.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val peers = node?.listPeers() ?: emptyList()
                    withContext(Dispatchers.Main) {
                        if (peers.isEmpty()) {
                            peersListText.text = "No peers connected"
                        } else {
                            val sb = StringBuilder()
                            peers.forEachIndexed { index, peer ->
                                sb.append("${index + 1}. ${peer.nodeId} (${peer.address})\n")
                            }
                            peersListText.text = sb.toString()
                        }
                    }
                } catch (e: Exception) {
                    showError("Failed to list peers: ${e.message}")
                }
            }
        }

        // Tier 3: Open Channel
        btnOpenChannel.setOnClickListener {
            val pubkey = channelPubkeyInput.text.toString().trim()
            val host = channelHostInput.text.toString().trim()
            val amountStr = channelAmountInput.text.toString().trim()

            if (pubkey.isEmpty() || host.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all channel fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amountSats = amountStr.toULongOrNull()
            if (amountSats == null) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pushToSats = 0UL

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    node?.openChannel(pubkey, host, amountSats, pushToSats, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Channel opening initiated!", Toast.LENGTH_LONG).show()
                        channelPubkeyInput.text.clear()
                        channelHostInput.text.clear()
                        channelAmountInput.text.clear()
                    }
                } catch (e: Exception) {
                    showError("Failed to open channel: ${e.message}")
                }
            }
        }
    }

    private fun initNode() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val storageDir = filesDir.absolutePath
                
                val builder = Builder()
                builder.setNetwork(Network.TESTNET)
                builder.setChainSourceEsplora("https://mempool.space/testnet/api", EsploraSyncConfig(300UL, 300UL, 300UL))
                builder.setStorageDirPath(storageDir)

                node = builder.build()
                
                val nodeId = node?.nodeId()

                withContext(Dispatchers.Main) {
                    nodeIdText.text = "Node ID:\n$nodeId"
                }

            } catch (e: Exception) {
                showError("Error initializing node: ${e.message}")
            }
        }
    }

    private suspend fun updateStatus(isRunning: Boolean) {
        withContext(Dispatchers.Main) {
            if (isRunning) {
                nodeStatusText.text = "● Running"
                nodeStatusText.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                nodeStatusText.text = "○ Stopped"
                nodeStatusText.setTextColor(Color.parseColor("#F44336"))
            }
        }
    }

    private suspend fun showError(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                node?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
