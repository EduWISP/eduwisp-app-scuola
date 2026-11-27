// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.eduwisp.scuola.sync.Controllo
import org.eduwisp.scuola.ui.AppScuola

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Controllo.pianifica(this)
        setContent { AppScuola(Sessione(this)) }
    }
}
