package com.example.nomina

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

enum class TipoContrato { NOMINA, PRESTACION_SERVICIOS }

data class NominaState(
    // ── Entradas comunes ──
    val salarioBruto: String = "",
    val tipoContrato: TipoContrato = TipoContrato.NOMINA,

    // ── Entradas nómina ──
    val fechaInicio: LocalDate = LocalDate.now().withDayOfMonth(1),
    val fechaFin: LocalDate = LocalDate.now(),
    val incluirPrima: Boolean = false,

    // ── Entradas prestación ──
    val porcentajeIBC: String = "40",

    // ── Resultados comunes ──
    val diasTrabajados: Int = 0,
    val salarioBase: Double = 0.0,
    val auxilioTransporte: Double = 0.0,
    val ibc: Double = 0.0,               // IBC expuesto para la UI
    val salud: Double = 0.0,
    val pension: Double = 0.0,           // Incluye FSP
    val fsp: Double = 0.0,               // Fondo Solidaridad (desglosado)
    val retencion: Double = 0.0,
    val prima: Double = 0.0,
    val salarioNeto: Double = 0.0,
    val esCalculado: Boolean = false,

    // ── Desglose para UI ──
    val detalleCalculo: String = ""
)

class NominaViewModel : ViewModel() {

    private val _state = MutableStateFlow(NominaState())
    val state: StateFlow<NominaState> = _state.asStateFlow()

    companion object {
        const val UVT_2026 = 53206.0
        const val SMMLV_2026 = 1750905.0
        const val AUX_TRANS_2026 = 249095.0
        const val TOP_IBC_MAX = 25 * SMMLV_2026

        const val TASA_SALUD_EMPLEADO = 0.04
        const val TASA_PENSION_EMPLEADO = 0.04
    }

    // ─────────────────────────────────────────────
    //  Setters — disparan recálculo automático
    // ─────────────────────────────────────────────

    fun onSalarioChange(valor: String) {
        _state.update { it.copy(salarioBruto = valor) }
        calcularNomina()
    }

    fun onTipoContratoChange(tipo: TipoContrato) {
        _state.update { it.copy(tipoContrato = tipo) }
        calcularNomina()
    }

    fun onFechaInicioChange(fecha: LocalDate) {
        _state.update {
            it.copy(
                fechaInicio = fecha,
                fechaFin = if (fecha.isAfter(it.fechaFin)) fecha else it.fechaFin
            )
        }
        calcularNomina()
    }

    fun onFechaFinChange(fecha: LocalDate) {
        _state.update {
            it.copy(fechaFin = if (fecha.isBefore(it.fechaInicio)) it.fechaInicio else fecha)
        }
        calcularNomina()
    }

    fun onIncluirPrimaChange(incluir: Boolean) {
        _state.update { it.copy(incluirPrima = incluir) }
        calcularNomina()
    }

    fun onPorcentajeIBCChange(valor: String) {
        _state.update { it.copy(porcentajeIBC = valor) }
        calcularNomina()
    }

    // ── Accionador Manual ─────────────────────────
    fun calcular() {
        val bruto = _state.value.salarioBruto.toDoubleOrNull() ?: 0.0
        if (bruto > 0.0) {
            _state.update { it.copy(esCalculado = true) }
            calcularNomina()
        }
    }

    // ─────────────────────────────────────────────
    //  Cálculo principal (automático)
    // ─────────────────────────────────────────────

    private fun calcularNomina() {
        val bruto = _state.value.salarioBruto.toDoubleOrNull() ?: 0.0
        if (bruto <= 0.0) {
            resetCalculos()
            return
        }
        when (_state.value.tipoContrato) {
            TipoContrato.NOMINA               -> calcularComoDependiente(bruto)
            TipoContrato.PRESTACION_SERVICIOS -> calcularComoIndependiente(bruto)
        }
    }

    // ── Nómina (dependiente) ──────────────────────
    private fun calcularComoDependiente(salarioMensual: Double) {
        val s = _state.value

        val diasTrabajados = calcularDiasComerciales(s.fechaInicio, s.fechaFin)
        val salarioProporcional = salarioMensual * diasTrabajados / 30.0

        val aplicaAuxilio = salarioMensual <= (2 * SMMLV_2026)
        val auxilioLiquidado = if (aplicaAuxilio) AUX_TRANS_2026 * diasTrabajados / 30.0 else 0.0

        // El auxilio de transporte NO entra al IBC
        val ibcCalculado = salarioProporcional.coerceIn(
            SMMLV_2026 * diasTrabajados / 30.0,
            TOP_IBC_MAX * diasTrabajados / 30.0
        )

        val salud        = ibcCalculado * TASA_SALUD_EMPLEADO
        val pension      = ibcCalculado * TASA_PENSION_EMPLEADO
        val fsp          = calcularFSP(salarioMensual) * diasTrabajados / 30.0
        val pensionTotal = pension + fsp

        val prima     = if (s.incluirPrima) salarioProporcional / 12.0 else 0.0
        val retencion = calcularRetencionNomina(salarioMensual, salud, pensionTotal)

        val neto = salarioProporcional + auxilioLiquidado - salud - pensionTotal - retencion + prima

        val detalle = buildString {
            append("Período: ${s.fechaInicio} → ${s.fechaFin}\n")
            append("Días trabajados: $diasTrabajados / 30\n")
            if (fsp > 0) append("  Fondo solidaridad: ${fmt(fsp)}\n")
        }

        _state.update {
            it.copy(
                diasTrabajados    = diasTrabajados,
                salarioBase       = salarioProporcional,
                auxilioTransporte = auxilioLiquidado,
                ibc               = ibcCalculado,
                salud             = salud,
                pension           = pensionTotal,
                fsp               = fsp,
                retencion         = retencion,
                prima             = prima,
                salarioNeto       = neto,
                detalleCalculo    = detalle
            )
        }
    }

    // ── Prestación de servicios (independiente) ───
    private fun calcularComoIndependiente(ingresoMensual: Double) {
        val s = _state.value
        val pct = (s.porcentajeIBC.toDoubleOrNull() ?: 40.0).coerceIn(40.0, 100.0) / 100.0

        val ibcCalculado = (ingresoMensual * pct).coerceIn(SMMLV_2026, TOP_IBC_MAX)

        val salud        = ibcCalculado * 0.125
        val pension      = ibcCalculado * 0.16
        val fsp          = calcularFSP(ingresoMensual)
        val pensionTotal = pension + fsp

        val retencion = calcularRetencionPrestacion(ingresoMensual)
        val neto = ingresoMensual - salud - pensionTotal - retencion

        val detalle = buildString {
            append("IBC (${(pct * 100).toInt()}%): ${fmt(ibcCalculado)}\n")
            if (ibcCalculado <= SMMLV_2026) append("  Ajustado al mínimo legal\n")
            if (fsp > 0) append("  Fondo solidaridad: ${fmt(fsp)}\n")
        }

        _state.update {
            it.copy(
                diasTrabajados    = 0,
                salarioBase       = ingresoMensual,
                auxilioTransporte = 0.0,
                ibc               = ibcCalculado,
                salud             = salud,
                pension           = pensionTotal,
                fsp               = fsp,
                retencion         = retencion,
                prima             = 0.0,
                salarioNeto       = neto,
                detalleCalculo    = detalle
            )
        }
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────

    private fun resetCalculos() {
        _state.update {
            it.copy(
                esCalculado = false,
                salarioNeto = 0.0, ibc = 0.0, salud = 0.0,
                pension = 0.0, fsp = 0.0, retencion = 0.0,
                prima = 0.0, auxilioTransporte = 0.0,
                diasTrabajados = 0, salarioBase = 0.0,
                detalleCalculo = ""
            )
        }
    }

    private fun calcularDiasComerciales(inicio: LocalDate, fin: LocalDate): Int {
        val d1 = inicio.dayOfMonth.coerceAtMost(30)
        val d2 = fin.dayOfMonth.coerceAtMost(30)
        val dias = (fin.year - inicio.year) * 360 +
                (fin.monthValue - inicio.monthValue) * 30 +
                (d2 - d1)
        return dias.coerceAtLeast(1)
    }

    private fun calcularFSP(salarioMensual: Double): Double {
        val s = SMMLV_2026
        return when {
            salarioMensual < 4  * s -> 0.0
            salarioMensual < 16 * s -> salarioMensual * 0.01
            salarioMensual < 17 * s -> salarioMensual * 0.012
            salarioMensual < 18 * s -> salarioMensual * 0.014
            salarioMensual < 19 * s -> salarioMensual * 0.016
            salarioMensual < 20 * s -> salarioMensual * 0.018
            else                     -> salarioMensual * 0.02
        }
    }

    private fun calcularRetencionNomina(
        salarioMensual: Double,
        salud: Double,
        pension: Double
    ): Double {
        val uvt = UVT_2026
        // Deducción automática por ley del 25% de Renta Exenta Laboral (Art 206 Numeral 10)
        val ingresosNetos = (salarioMensual - salud - pension).coerceAtLeast(0.0)
        val baseGravable = (ingresosNetos * 0.75).coerceAtLeast(0.0)
        val baseUvt = baseGravable / uvt

        return when {
            baseUvt < 95  -> 0.0
            baseUvt < 150 -> (baseGravable - 95  * uvt) * 0.19
            baseUvt < 360 -> (baseGravable - 150 * uvt) * 0.28 + (10.38 * uvt)
            baseUvt < 640 -> (baseGravable - 360 * uvt) * 0.33 + (69.18 * uvt)
            else           -> (baseGravable - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    private fun calcularRetencionPrestacion(ingreso: Double): Double {
        val uvt = UVT_2026
        // Para contratistas tomamos un estimado prudente de costos deducibles del 20%
        val base = ingreso * 0.80
        val baseUvt = base / uvt
        return when {
            baseUvt < 95  -> 0.0
            baseUvt < 150 -> (base - 95  * uvt) * 0.19
            baseUvt < 360 -> (base - 150 * uvt) * 0.28 + (10.38 * uvt)
            baseUvt < 640 -> (base - 360 * uvt) * 0.33 + (69.18 * uvt)
            else           -> (base - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    private fun fmt(valor: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }.format(valor)
    }
}