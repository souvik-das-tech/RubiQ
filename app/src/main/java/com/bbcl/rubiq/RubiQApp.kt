package com.bbcl.rubiq

import android.app.Application

class RubiQApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load the solver tables in the background while the user paints.
        SolverService.start(this)
    }
}
