package com.example.nomina

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
// Opciones únicas para el tipo de vinculación laboral
enum class TipoContrato { NOMINA, PRESTACION_SERVICIOS }

// Representa el estado único de la pantalla (Fotografía de los datos)
data class NominaState(
    // ── Entradas comunes (Inputs de la UI) ──
    val salarioBruto: String = "", // Salario digitado por el usuario (en texto)
    val tipoContrato: TipoContrato = TipoContrato.NOMINA, // Contrato seleccionado

    // ── Entradas nómina ──
    val fechaInicio: LocalDate = LocalDate.now().withDayOfMonth(1), // Primer día del mes actual
    val fechaFin: LocalDate = LocalDate.now(), // Día actual
    val incluirPrima: Boolean = false, // Interruptor para liquidar prima

    // ── Entradas prestación ──
    val porcentajeIBC: String = "40", // Porcentaje base de cotización para independientes

    // ── Resultados comunes (Outputs que lee la UI) ──
    val diasTrabajados: Int = 0, // Días comerciales liquidados
    val salarioBase: Double = 0.0, // Sueldo proporcional a los días trabajados
    val auxilioTransporte: Double = 0.0, // Auxilio de transporte proporcional
    val ibc: Double = 0.0,               // Ingreso Base de Cotización final
    val salud: Double = 0.0,             // Descuento de salud
    val pension: Double = 0.0,           // Descuento de pensión (Básica + FSP)
    val fsp: Double = 0.0,               // Desglose del Fondo de Solidaridad
    val retencion: Double = 0.0,         // Descuento por retención en la fuente
    val prima: Double = 0.0,             // Valor de la prima prestacional
    val salarioNeto: Double = 0.0,       // Pago final neto en cuenta
    val esCalculado: Boolean = false,    // Bandera para mostrar/ocultar resultados en la UI
    val detalleCalculo: String = ""      // Resumen descriptivo en texto
)

// El "cerebro" encargado de procesar la lógica de negocio de la pantalla
class NominaViewModel : ViewModel() {
    // Estado mutable interno (Lectura y Escritura exclusiva del ViewModel)
    private val _state = MutableStateFlow(NominaState())
    // Estado público de solo lectura para ser escuchado por la interfaz (Compose)
    val state: StateFlow<NominaState> = _state.asStateFlow()

    // Constantes legales vigentes en Colombia para el año 2026
    companion object {
        const val UVT_2026 = 53206.0 // Unidad de Valor Tributario
        const val SMMLV_2026 = 1750905.0 // Salario Mínimo Mensual Legal Vigente
        const val AUX_TRANS_2026 = 249095.0 // Auxilio de Transporte completo
        const val TOP_IBC_MAX = 25 * SMMLV_2026 // Límite máximo de cotización (25 salarios mínimos)

        const val TASA_SALUD_EMPLEADO = 0.04 // Descuento del 4% para empleados de nómina
        const val TASA_PENSION_EMPLEADO = 0.04 // Descuento del 4% para empleados de nómina
    }

    // ── Setters ── Disparan la actualización del estado y recalculan de inmediato

    // Se ejecuta al escribir en el campo de sueldo bruto
    fun onSalarioChange(valor: String) {
        _state.update { it.copy(salarioBruto = valor) }
        calcularNomina()
    }

    // Se ejecuta al cambiar el tipo de contrato en el desplegable
    fun onTipoContratoChange(tipo: TipoContrato) {
        _state.update { it.copy(tipoContrato = tipo) }
        calcularNomina()
    }

    // Se ejecuta al cambiar la fecha de inicio (Valida que el fin no quede antes del inicio)
    fun onFechaInicioChange(fecha: LocalDate) {
        _state.update {
            it.copy(
                fechaInicio = fecha,
                fechaFin = if (fecha.isAfter(it.fechaFin)) fecha else it.fechaFin
            )
        }
        calcularNomina()
    }

    // Se ejecuta al cambiar la fecha de fin (Evita que sea anterior al inicio)
    fun onFechaFinChange(fecha: LocalDate) {
        _state.update {
            it.copy(fechaFin = if (fecha.isBefore(it.fechaInicio))it.fechaInicio else fecha)
        }
        calcularNomina()
    }

    // Se ejecuta al activar/desactivar el checkbox de la prima
    fun onIncluirPrimaChange(incluir: Boolean) {
        _state.update { it.copy(incluirPrima = incluir) }
        calcularNomina()
    }

    // Se ejecuta al cambiar el porcentaje del IBC en prestación de servicios
    fun onPorcentajeIBCChange(valor: String) {
        _state.update { it.copy(porcentajeIBC = valor) }
        calcularNomina()
    }

    // ── Accionador Manual (Botón calcular) ─────────────────────────
    fun calcular() {
        val bruto = _state.value.salarioBruto.toDoubleOrNull() ?: 0.0
        if (bruto > 0.0) {
            // Activa la visualización del panel de resultados en la pantalla
            _state.update { it.copy(esCalculado = true) }
            calcularNomina()
        }
    }

    // Enrutador de cálculos automáticos
    private fun calcularNomina() {
        val bruto = _state.value.salarioBruto.toDoubleOrNull() ?: 0.0
        // Si el salario es cero o erróneo, limpia todos los números y frena la ejecución
        if (bruto <= 0.0) {
            resetCalculos()
            return
        }
        // Elige las fórmulas matemáticas según el tipo de contrato seleccionado
        when (_state.value.tipoContrato) {
            TipoContrato.NOMINA               -> calcularComoDependiente(bruto)
            TipoContrato.PRESTACION_SERVICIOS -> calcularComoIndependiente(bruto)
        }
    }

    // ── Nómina (dependiente) ──────────────────────
    private fun calcularComoDependiente(salarioMensual: Double) {
        val s = _state.value
        // 1. Días y salarios proporcionales
        val diasTrabajados = calcularDiasComerciales(s.fechaInicio, s.fechaFin)
        val salarioProporcional = salarioMensual * diasTrabajados / 30.0

        // 2. Auxilio de Transporte (Regla de los 2 SMMLV máximos)
        val aplicaAuxilio = salarioMensual <= (2 * SMMLV_2026)
        val auxilioLiquidado = if (aplicaAuxilio) AUX_TRANS_2026 * diasTrabajados / 30.0 else 0.0

        // 3. IBC (Se calcula sobre el devengado sin Auxilio de Transporte y controlado por topes)
        val ibcCalculado = salarioProporcional.coerceIn(
            SMMLV_2026 * diasTrabajados / 30.0,
            TOP_IBC_MAX * diasTrabajados / 30.0
        )

        // 4. Seguridad Social obligatoria
        val salud        = ibcCalculado * TASA_SALUD_EMPLEADO // 4%
        val pension      = ibcCalculado * TASA_PENSION_EMPLEADO // 4%
        val fsp          = calcularFSP(salarioMensual) * diasTrabajados / 30.0 // Fondo de Solidaridad
        val pensionTotal = pension + fsp

        // 5. Prestaciones Sociales e impuestos
        val prima     = if (s.incluirPrima) salarioProporcional / 12.0 else 0.0 // Proporción de un mes al año
        val retencion = calcularRetencionNomina(salarioMensual, salud, pensionTotal) // Retefuente

        // 6. Ecuación del salario neto definitivo
        val neto = salarioProporcional + auxilioLiquidado - salud - pensionTotal - retencion + prima

        // Genera el texto técnico descriptivo para la UI
        val detalle = buildString {
            append("Período: ${s.fechaInicio} → ${s.fechaFin}\n")
            append("Días trabajados: $diasTrabajados / 30\n")
            if (fsp > 0) append("  Fondo solidaridad: ${fmt(fsp)}\n")
        }

        // Envía todo el paquete de resultados procesados al flujo de estado
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
        // Convierte el porcentaje de texto a decimal (ej: "40" -> 0.40) limitándolo entre el 40% y el 100%
        val pct = (s.porcentajeIBC.toDoubleOrNull() ?: 40.0).coerceIn(40.0, 100.0) / 100.0
        // El IBC de un independiente no puede ser menor a 1 sueldo mínimo ni mayor a 25
        val ibcCalculado = (ingresoMensual * pct).coerceIn(SMMLV_2026, TOP_IBC_MAX)

        // El independiente paga la totalidad de los aportes de seguridad social
        val salud        = ibcCalculado * 0.125 // 12.5% por ley
        val pension      = ibcCalculado * 0.16  // 16% por ley
        val fsp          = calcularFSP(ingresoMensual) // Solidaridad si aplica
        val pensionTotal = pension + fsp

        // Retención en la fuente específica para independientes
        val retencion = calcularRetencionPrestacion(ingresoMensual)

        // Ecuación neta: Ingreso total menos sus deducciones obligatorias
        val neto = ingresoMensual - salud - pensionTotal - retencion

        // Construcción de detalles técnicos para independientes
        val detalle = buildString {
            append("IBC (${(pct * 100).toInt()}%): ${fmt(ibcCalculado)}\n")
            if (ibcCalculado <= SMMLV_2026) append("  Ajustado al mínimo legal\n")
            if (fsp > 0) append("  Fondo solidaridad: ${fmt(fsp)}\n")
        }

        // Envía el estado limpio y con ceros en las variables que no aplican a esta modalidad
        _state.update {
            it.copy(
                diasTrabajados    = 0, // No aplica días comerciales
                salarioBase       = ingresoMensual,
                auxilioTransporte = 0.0, // No tiene derecho por ley
                ibc               = ibcCalculado,
                salud             = salud,
                pension           = pensionTotal,
                fsp               = fsp,
                retencion         = retencion,
                prima             = 0.0, // No recibe prima prestacional
                salarioNeto       = neto,
                detalleCalculo    = detalle
            )
        }
    }

    // ── Helpers / Funciones de utilidad interna ─────────────────────────

    // Regresa el panel de resultados a ceros absolutos
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

    // Algoritmo matemático para hallar días bajo el estándar contable (Todos los meses = 30 días)
    private fun calcularDiasComerciales(inicio: LocalDate, fin: LocalDate): Int {
        val d1 = inicio.dayOfMonth.coerceAtMost(30) // Si es 31, lo toma como 30
        val d2 = fin.dayOfMonth.coerceAtMost(30)    // Si es 31, lo toma como 30
        val dias = (fin.year - inicio.year) * 360 +
                (fin.monthValue - inicio.monthValue) * 30 +
                (d2 - d1)
        return dias.coerceAtLeast(1) // Asegura como mínimo retornar 1 día trabajado
    }

    // Escala progresiva de cobro para el Fondo de Solidaridad Pensional (FSP)
    private fun calcularFSP(salarioMensual: Double): Double {
        val s = SMMLV_2026
        return when {
            salarioMensual < 4  * s -> 0.0               // Menos de 4 mínimos: exento
            salarioMensual < 16 * s -> salarioMensual * 0.01   // 4 a 16 SMMLV: 1%
            salarioMensual < 17 * s -> salarioMensual * 0.012  // 16 a 17 SMMLV: 1.2%
            salarioMensual < 18 * s -> salarioMensual * 0.014  // 17 a 18 SMMLV: 1.4%
            salarioMensual < 19 * s -> salarioMensual * 0.016  // 18 a 19 SMMLV: 1.6%
            salarioMensual < 20 * s -> salarioMensual * 0.018  // 19 a 20 SMMLV: 1.8%
            else                     -> salarioMensual * 0.02   // Más de 20 SMMLV: 2%
        }
    }

    // Liquidación de Retención en la fuente para empleados (Art. 383 E.T. + Deducción 25% exento)
    private fun calcularRetencionNomina(
        salarioMensual: Double,
        salud: Double,
        pension: Double
    ): Double {
        val uvt = UVT_2026
        // Se restan los ingresos no constitutivos de renta (salud y pensión obligatoria)
        val ingresosNetos = (salarioMensual - salud - pension).coerceAtLeast(0.0)
        // Aplica el beneficio legal del 25% de exención laboral (Se cobra sobre el 75% sobrante)
        val baseGravable = (ingresosNetos * 0.75).coerceAtLeast(0.0)
        val baseUvt = baseGravable / uvt // Convierte la base de pesos colombianos a unidades UVT

        // Tabla de retención marginal por rangos de UVT
        return when {
            baseUvt < 95  -> 0.0 // Rango exento
            baseUvt < 150 -> (baseGravable - 95  * uvt) * 0.19
            baseUvt < 360 -> (baseGravable - 150 * uvt) * 0.28 + (10.38 * uvt)
            baseUvt < 640 -> (baseGravable - 360 * uvt) * 0.33 + (69.18 * uvt)
            else           -> (baseGravable - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    // Liquidación simplificada de Retención en la fuente para independientes contratistas
    private fun calcularRetencionPrestacion(ingreso: Double): Double {
        val uvt = UVT_2026
        // Aplica un porcentaje del 20% deducible estimado por costos y gastos operativos del contratista
        val base = ingreso * 0.80
        val baseUvt = base / uvt // Convierte pesos a UVT

        // Aplica la misma tabla de tarifas del Artículo 383 del Estatuto Tributario
        return when {
            baseUvt < 95  -> 0.0
            baseUvt < 150 -> (base - 95  * uvt) * 0.19
            baseUvt < 360 -> (base - 150 * uvt) * 0.28 + (10.38 * uvt)
            baseUvt < 640 -> (base - 360 * uvt) * 0.33 + (69.18 * uvt)
            else           -> (base - 640 * uvt) * 0.39 + (161.58 * uvt)
        }
    }

    // Formateador local de números a dinero estructurado para textos rápidos internos
    private fun fmt(valor: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }.format(valor)
    }
}