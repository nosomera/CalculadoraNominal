package com.example.nominacalculadora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nomina.NominaScreen

import com.example.nominacalculadora.ui.theme.NominaCalculadoraTheme
import com.example.nominacalculadora.*
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NominaCalculadoraTheme {
                NominaScreen()
            }
        }
    }
}