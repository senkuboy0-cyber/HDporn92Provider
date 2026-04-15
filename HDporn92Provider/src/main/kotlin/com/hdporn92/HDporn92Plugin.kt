package com.hdporn92

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HDporn92Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDporn92Provider())
    }
}
