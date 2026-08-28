package sk.ziacik.androidtvplayer.systemepg

import android.app.Activity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KanalikTvInputSetupActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val publisher = SystemEpgPublisher(this@KanalikTvInputSetupActivity)
            val catalog = KanalikCatalog(this@KanalikTvInputSetupActivity).load()
            runCatching { publisher.publishChannels(catalog) }
                .onSuccess { channels ->
                    setResult(RESULT_OK)
                    SystemEpgSyncWorker.enqueue(this@KanalikTvInputSetupActivity)
                    finish()
                }
                .onFailure {
                    Log.e("SystemEpg", "Initial system EPG synchronization failed", it)
                    setResult(RESULT_CANCELED)
                }
        }
    }
}
