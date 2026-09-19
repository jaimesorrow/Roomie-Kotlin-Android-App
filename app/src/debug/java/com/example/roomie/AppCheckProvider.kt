package com.example.roomie

import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckProvider {
    fun factory() = DebugAppCheckProviderFactory.getInstance()
}
