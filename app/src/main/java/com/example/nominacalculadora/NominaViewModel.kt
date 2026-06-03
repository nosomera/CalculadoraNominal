package com.example.nomina

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

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
    val porcentajeIBC: String = "40",   // % del ingreso que es base de cotización

    // ── Resultados comunes ──
    val diasTrabajados: Int = 0,
    val salarioBase: Double = 0.0,      // salario proporcional a días
    val auxilioTransporte: Double = 0.0, // Auxilio de transporte liquidado
    val salud: Double = 0.0,
    val pension: Double = 0.0,
    val retencion: Double = 0.0,
    val prima: Double = 0.0,
    val salarioNeto: Double = 0.0,
    val calculado: Boolean = false,

    // ── Desglose para UI ──
    val detalleCalculo: String = ""
)

class NominaViewModel : ViewModel() {

    private val _state = MutableStateFlow(NominaState())
    val state: StateFlow<NominaState> = _state.asStateFlow()

    // ─────────────────────────────────────────────
    //  Constantes legales Colombia Actualizadas
    // ─────────────────────────────────────────────
    companion object {
        const val UVT_2026 = 53206.0

        // Valores vigentes solicitados
        const val SMMLV_ACTUAL = 1750905.0
        const val AUXILIO_TRANSPORTE_ACTUAL = 249095.0

        // Tasas prestacionales y de seguridad social
        const val TASA_SALUD_EMPLEADO = 0.04          // 4%
        const val TASA_PENSION_EMPLEADO = 0.04        // 4%
        const val TASA_PENSION_EMPLEADOR = 0.12
        const val FACTOR_PRIMA_MENSUAL = 1.0 / 12.0
        const val DIAS_ANIO_COMERCIAL = 360
    }

    // ─────────────────────────────────────────────
    //  Setters de estado
    // ─────────────────────────────────────────────

    fun onSalarioChange(valor: String) {
        _state.update { it.copy(salarioBruto = valor, calculado = false) }
    }

    fun onTipoContratoChange(tipo: TipoContrato) {
        _state.update { it.copy(tipoContrato = tipo, calculado = false) }
    }

    fun onFechaInicioChange(fecha: LocalDate) {
        _state.update {
            it.copy(
                fechaInicio = fecha,
                fechaFin = if (fecha.isAfter(it.fechaFin)) fecha else it.fechaFin,
                calculado = false
            )
        }
    }

    fun onFechaFinChange(fecha: LocalDate) {
        _state.update {
            it.copy(
                fechaFin = if (fecha.isBefore(it.fechaInicio)) it.fechaInicio else fecha,
                calculado = false
            )
        }
    }

    fun onIncluirPrimaChange(incluir: Boolean) {
        _state.update { it.copy(incluirPrima = incluir, calculado = false) }
    }

    fun onPorcentajeIBCChange(valor: String) {
        _state.update { it.copy(porcentajeIBC = valor, calculado = false) }
    }

    // ─────────────────────────────────────────────
    //  Cálculo principal
    // ─────────────────────────────────────────────

    fun calcular() {
        val brutoMensual = _state.value.salarioBruto.toDoubleOrNull() ?: return
        val tipo = _state.value.tipoContrato

        if (tipo == TipoContrato.NOMINA) {
            calcularNomina(brutoMensual)
        } else {
            calcularPrestacion(brutoMensual)
        }
    }

    // ── Nómina ───────────────────────────────────
    private fun calcularNomina(salarioMensual: Double) {
        val s = _state.value

        // 1. Días trabajados (calendario comercial)
        val diasTrabajados = calcularDiasComerciales(s.fechaInicio, s.fechaFin)

        // 2. Salario básico proporcional al período (sin auxilio de transporte)
        val salarioProporcional = salarioMensual * diasTrabajados / 30.0

        // 3. Auxilio de transporte proporcional (Aplica si el salario básico mensual <= 2 SMMLV)
        val aplicaAuxilio = salarioMensual <= (2 * SMMLV_ACTUAL)
        val auxilioLiquidado = if (aplicaAuxilio) {
            AUXILIO_TRANSPORTE_ACTUAL * diasTrabajados / 30.0
        } else 0.0

        // 4. Base de cotización (IBC) -> El auxilio de transporte NO entra al IBC
        val ibc = salarioProporcional.coerceIn(
            SMMLV_ACTUAL * diasTrabajados / 30.0,
            25 * SMMLV_ACTUAL * diasTrabajados / 30.0
        )

        // 5. Deducciones legales sobre el IBC
        val salud = ibc * TASA_SALUD_EMPLEADO
        val pension = ibc * TASA_PENSION_EMPLEADO
        val solidaridad = calcularFondoSolidaridad(salarioMensual) * diasTrabajados / 30.0
        val pensionTotal = pension + solidaridad

        // 6. Prima de servicios (opcional) -> Nota contable: legalmente la prima sí incluye auxilio de transporte,
        // pero para mantener la consistencia del salario base neto mensual, se calcula sobre el proporcional básico.
        val prima = if (s.incluirPrima) {
            salarioProporcional / 12.0
        } else 0.0

        // 7. Retención en la fuente (sobre ingresos gravables)
        val retencion = calcularRetencionNomina(salarioMensual, salud, pensionTotal)

        // 8. Neto Final Recibido (Se restan descuentos y SE SUMA el auxilio de transporte y la prima)
        val neto = salarioProporcional + auxilioLiquidado - salud - pensionTotal - retencion + prima

        val detalle = buildString {
            append("Período: ${s.fechaInicio} → ${s.fechaFin}\n")
            append("Días trabajados: $diasTrabajados / 30\n")
            append("Salario proporcional: ${fmt(salarioProporcional)}\n")
            if (auxilioLiquidado > 0) append("Auxilio de Transporte: ${fmt(auxilioLiquidado)}\n")
            append("IBC (Salud/Pensión): ${fmt(ibc)}\n")
            if (solidaridad > 0) append("  Fondo solidaridad: ${fmt(solidaridad)}\n")
            if (s.incluirPrima) append("Prima proporcional: ${fmt(prima)}\n")
        }

        _state.update {
            it.copy(
                diasTrabajados = diasTrabajados,
                salarioBase = salarioProporcional,
                auxilioTransporte = auxilioLiquidado,
                salud = salud,
                pension = pensionTotal,
                retencion = retencion,
                prima = prima,
                salarioNeto = neto,
                calculado = true,
                detalleCalculo = detalle
            )
        }
    }

    // ── Prestación de servicios ───────────────────
    private fun calcularPrestacion(ingresoMensual: Double) {
        val s = _state.value
        val pct = (s.porcentajeIBC.toDoubleOrNull() ?: 40.0).coerceIn(40.0, 100.0) / 100.0

        val ibcCalculado = ingresoMensual * pct
        val ibc = ibcCalculado.coerceAtLeast(SMMLV_ACTUAL)

        val salud = ibc * 0.125
        val pension = ibc * 0.16
        val solidaridad = calcularFondoSolidaridad(ingresoMensual)
        val pensionTotal = pension + solidaridad

        val retencion = calcularRetencionPrestacion(ingresoMensual)

        val neto = ingresoMensual - salud - pensionTotal - retencion

        val detalle = buildString {
            append("IBC (${(pct * 100).toInt()}%): ${fmt(ibc)}\n")
            if (ibcCalculado < SMMLV_ACTUAL) append("  Ajustado al mínimo legal\n")
            if (solidaridad > 0) append("  Fondo solidaridad: ${fmt(solidaridad)}\n")
        }

        _state.update {
            it.copy(
                diasTrabajados = 0,
                salarioBase = ingresoMensual,
                auxilioTransporte = 0.0, // No aplica en prestación de servicios
                salud = salud,
                pension = pensionTotal,
                retencion = retencion,
                prima = 0.0,
                salarioNeto = neto,
                calculado = true,
                detalleCalculo = detalle
            )
        }
    }

    // ─────────────────────────────────────────────
    //  Helpers de cálculo
    // ─────────────────────────────────────────────

    private fun calcularDiasComerciales(inicio: LocalDate, fin: LocalDate): Int {
        val d1 = inicio.dayOfMonth.coerceAtMost(30)
        val d2 = fin.dayOfMonth.coerceAtMost(30)
        val dias = (fin.year - inicio.year) * 360 +
                (fin.monthValue - inicio.monthValue) * 30 +
                (d2 - d1)
        return dias.coerceAtLeast(1)
    }

    private fun calcularFondoSolidaridad(salarioMensual: Double): Double {
        val smmlv = SMMLV_ACTUAL
        return when {
            salarioMensual < 4 * smmlv  -> 0.0
            salarioMensual < 16 * smmlv -> salarioMensual * 0.01
            salarioMensual < 17 * smmlv -> salarioMensual * 0.012
            salarioMensual < 18 * smmlv -> salarioMensual * 0.014
            salarioMensual < 19 * smmlv -> salarioMensual * 0.016
            salarioMensual < 20 * smmlv -> salarioMensual * 0.018
            else                         -> salarioMensual * 0.02
        }
    }

    private fun calcularRetencionNomina(
        salarioMensual: Double,
        salud: Double,
        pension: Double
    ): Double {
        val uvt = UVT_2026
        val baseGravable = (salarioMensual - salud - pension).coerceAtLeast(0.0)
        val baseUvt = baseGravable / uvt

        return when {
            baseUvt < 95   -> 0.0
            baseUvt < 150  -> (baseGravable - 95 * uvt) * 0.19
            baseUvt < 360  -> (baseGravable - 150 * uvt) * 0.28 + (10.38 * uvt)
            baseUvt < 640  -> (baseGravable - 360 * uvt) * 0.33 + (69.18 * uvt)
            else            -> (baseGravable - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    private fun calcularRetencionPrestacion(ingreso: Double): Double {
        val uvt = UVT_2026
        val base = ingreso * 0.80

        return when {
            ingreso < 4 * uvt  -> 0.0
            ingreso < 95 * uvt -> base * 0.11
            ingreso < 150 * uvt -> (base - 95 * uvt) * 0.19
            ingreso < 360 * uvt -> (base - 150 * uvt) * 0.28 + (10.38 * uvt)
            ingreso < 640 * uvt -> (base - 360 * uvt) * 0.33 + (69.18 * uvt)
            else                 -> (base - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    private fun fmt(v: Double) = "%,.0f".format(v)
}