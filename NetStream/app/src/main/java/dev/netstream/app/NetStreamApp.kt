package dev.netstream.app

import android.app.Application
import dev.netstream.data.NetStreamData

class NetStreamApp : Application() {
    val data by lazy { NetStreamData(this, BuildConfig.CLOUD_ENABLED) }
}
