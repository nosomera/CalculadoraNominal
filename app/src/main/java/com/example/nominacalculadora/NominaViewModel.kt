package com.example.nomina

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TipoContrato { NOMINA, PRESTACION_SERVICIOS }

data class NominaState(
    val salarioBruto: String = "",
    val tipoContrato: TipoContrato = TipoContrato.NOMINA,
    val salud: Double = 0.0,
    val pension: Double = 0.0,
    val retencion: Double = 0.0,
    val salarioNeto: Double = 0.0,
    val calculado: Boolean = false
)

class NominaViewModel : ViewModel() {

    private val _state = MutableStateFlow(NominaState())
    val state: StateFlow<NominaState> = _state.asStateFlow()

    fun onSalarioChange(valor: String) {
        _state.update { it.copy(salarioBruto = valor, calculado = false) }
    }

    fun onTipoContratoChange(tipo: TipoContrato) {
        _state.update { it.copy(tipoContrato = tipo, calculado = false) }
    }

    fun calcular() {
        val bruto = _state.value.salarioBruto.toDoubleOrNull() ?: return
        val tipo = _state.value.tipoContrato

        val salud: Double
        val pension: Double
        val retencion: Double

        if (tipo == TipoContrato.NOMINA) {
            // Empleado de nómina: descuentos obligatorios
            salud = bruto * 0.04       // 4% salud
            pension = bruto * 0.04     // 4% pensión
        } else {
            // Prestación de servicios: no hay descuentos obligatorios del empleador,
            // pero el contratista aporta voluntariamente o según la ley
            salud = bruto * 0.125 * 0.04   // base del 40% de ingresos x 12.5% IBC x 4%
            pension = bruto * 0.125 * 0.04
        }

        // Retención en la fuente según rangos (en pesos colombianos)
        retencion = calcularRetencion(bruto, tipo)

        val neto = bruto - salud - pension - retencion

        _state.update {
            it.copy(
                salud = salud,
                pension = pension,
                retencion = retencion,
                salarioNeto = neto,
                calculado = true
            )
        }
    }

    private fun calcularRetencion(bruto: Double, tipo: TipoContrato): Double {
        // Rangos simplificados en pesos colombianos (UVT 2024 ≈ $47,065)
        val uvt = 47065.0
        val base = if (tipo == TipoContrato.PRESTACION_SERVICIOS) bruto * 0.80 else bruto

        return when {
            base < 95 * uvt  -> 0.0                         // Sin retención
            base < 150 * uvt -> (base - 95 * uvt) * 0.19   // Tarifa 19%
            base < 360 * uvt -> (base - 150 * uvt) * 0.28 + (55 * uvt * 0.19)  // 28%
            else             -> (base - 360 * uvt) * 0.33 + (55 * uvt * 0.19) + (210 * uvt * 0.28) // 33%
        }
    }
}