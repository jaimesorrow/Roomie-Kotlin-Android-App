package com.example.roomie

import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckProvider {
    fun factory() = PlayIntegrityAppCheckProviderFactory.getInstance()
}
