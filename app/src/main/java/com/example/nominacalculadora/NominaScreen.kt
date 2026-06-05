package com.example.nomina

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  Formateadores globales
// ─────────────────────────────────────────────────────────────────────────────
private val FMT_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FMT_COP: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
    maximumFractionDigits = 0
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla principal
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NominaScreen(viewModel: NominaViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var expandedSpinner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora de Nómina 2026") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── 1. Salario bruto ─────────────────────────────────────────────
            OutlinedTextField(
                value = formatearPuntosMiles(state.salarioBruto),
                onValueChange = { nuevoTexto ->
                    val soloDigitos = nuevoTexto.filter { it.isDigit() }
                    // Evitamos desbordamientos de enteros (Máximo 12 dígitos)
                    if (soloDigitos.length <= 12) {
                        viewModel.onSalarioChange(soloDigitos)
                    }
                },
                label = { Text("Salario Bruto (COP)") },
                placeholder = { Text("Ej: 2.000.000") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── 2. Spinner tipo de contrato ──────────────────────────────────
            Text("Tipo de contrato", style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(
                expanded = expandedSpinner,
                onExpandedChange = { expandedSpinner = !expandedSpinner }
            ) {
                OutlinedTextField(
                    value = if (state.tipoContrato == TipoContrato.NOMINA)
                        "Nómina" else "Prestación de servicios",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSpinner) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedSpinner,
                    onDismissRequest = { expandedSpinner = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nómina") },
                        onClick = {
                            viewModel.onTipoContratoChange(TipoContrato.NOMINA)
                            expandedSpinner = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Prestación de servicios") },
                        onClick = {
                            viewModel.onTipoContratoChange(TipoContrato.PRESTACION_SERVICIOS)
                            expandedSpinner = false
                        }
                    )
                }
            }

            // ── 3a. Opciones exclusivas de NÓMINA ────────────────────────────
            AnimatedVisibility(
                visible = state.tipoContrato == TipoContrato.NOMINA,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    HorizontalDivider()
                    Text(
                        "Período laborado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Selector fecha inicio
                    DatePickerField(
                        label = "Fecha inicio",
                        fecha = state.fechaInicio,
                        onFechaSeleccionada = { viewModel.onFechaInicioChange(it) }
                    )

                    // Selector fecha fin
                    DatePickerField(
                        label = "Fecha fin",
                        fecha = state.fechaFin,
                        onFechaSeleccionada = { viewModel.onFechaFinChange(it) },
                        minDate = state.fechaInicio
                    )

                    // Info días comerciales proyectados
                    if (!state.esCalculado) {
                        val diasPrev = calcularDiasPrev(state.fechaInicio, state.fechaFin)
                        InfoChip("Días comerciales estimados: $diasPrev / 30")
                    }

                    // Checkbox prima
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = state.incluirPrima,
                            onCheckedChange = { viewModel.onIncluirPrimaChange(it) }
                        )
                        Column {
                            Text(
                                "Incluir prima de servicios",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "≈ salario / 12 proporcional al período",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 3b. Opciones exclusivas de PRESTACIÓN ────────────────────────
            AnimatedVisibility(
                visible = state.tipoContrato == TipoContrato.PRESTACION_SERVICIOS,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    HorizontalDivider()
                    Text(
                        "Base de cotización (IBC)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = state.porcentajeIBC,
                        onValueChange = { nuevoValor ->
                            // Permitimos únicamente dígitos numéricos
                            viewModel.onPorcentajeIBCChange(nuevoValor.filter { it.isDigit() })
                        },
                        label = { Text("% del ingreso como IBC") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        suffix = { Text("%") },
                        supportingText = { Text("Mínimo legal: 40%  |  Máximo: 100%") }
                    )

                    InfoChip("Salud: 12.5% del IBC  •  Pensión: 16% del IBC (aporte contratista)")
                }
            }

            // ── 4. Botón calcular ─────────────────────────────────────────────
            Button(
                onClick = { viewModel.calcular() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.salarioBruto.isNotBlank()
            ) {
                Text("Calcular salario neto")
            }

            // ── 5. Resultados ────────────────────────────────────────────────
            AnimatedVisibility(visible = state.esCalculado) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    HorizontalDivider()

                    Text(
                        "Resumen de descuentos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Detalle de período (solo nómina)
                    if (state.tipoContrato == TipoContrato.NOMINA) {
                        InfoChip(
                            "Días trabajados: ${state.diasTrabajados} / 30  " +
                                    "— Proporcional: ${FMT_COP.format(state.salarioBase)}"
                        )
                    }

                    // Salario bruto / ingreso
                    FilaResultado(
                        "Ingreso bruto",
                        state.salarioBruto.toDoubleOrNull() ?: 0.0,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Auxilio de Transporte (si aplica)
                    if (state.auxilioTransporte > 0) {
                        FilaResultado(
                            "(+) Auxilio de transporte",
                            state.auxilioTransporte,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Salud
                    FilaResultado(
                        if (state.tipoContrato == TipoContrato.NOMINA)
                            "(-) Salud empleado 4%"
                        else
                            "(-) Salud contratista 12.5%",
                        state.salud
                    )

                    // Pensión
                    FilaResultado(
                        if (state.tipoContrato == TipoContrato.NOMINA)
                            "(-) Pensión empleado 4%"
                        else
                            "(-) Pensión contratista 16%",
                        state.pension - state.fsp // Restamos el fsp para mostrar la pensión básica limpia
                    )

                    // Fondo de solidaridad pensional (FSP) de forma clara e independiente
                    if (state.fsp > 0) {
                        FilaResultado(
                            "(-) Fondo Solidaridad Pensional (FSP)",
                            state.fsp
                        )
                    }

                    // Retención en la fuente
                    if (state.retencion > 0) {
                        FilaResultado("(-) Retención en la fuente", state.retencion)
                    }

                    // Prima proporcional
                    if (state.prima > 0) {
                        FilaResultado(
                            "(+) Prima de servicios",
                            state.prima,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    HorizontalDivider()

                    // ── Salario neto destacado ─────────────────────────────
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Salario neto recibido",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (state.tipoContrato == TipoContrato.NOMINA && state.incluirPrima) {
                                    Text(
                                        "Incluye prima legal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Text(
                                FMT_COP.format(state.salarioNeto),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ── Aviso legal ───────────────────────────────────────
                    Text(
                        "* Cálculo orientativo basado en la normativa colombiana vigente " +
                                "(UVT 2026 proyectado: ${FMT_COP.format(NominaViewModel.UVT_2026)}). " +
                                "Valide siempre con su software contable de nómina electrónica.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Componente: selector de fecha con DatePickerDialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    fecha: LocalDate,
    onFechaSeleccionada: (LocalDate) -> Unit,
    minDate: LocalDate? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fecha.format(FMT_FECHA),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar $label")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (showDialog) {
        val initialMillis = fecha
            .atStartOfDay()
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = if (minDate != null) {
                val minMillis = minDate
                    .atStartOfDay()
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minMillis
                }
            } else {
                object : SelectableDates {}
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        onFechaSeleccionada(selected)
                    }
                    showDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Componentes auxiliares
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FilaResultado(
    label: String,
    valor: Double,
    color: Color = MaterialTheme.colorScheme.error
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            FMT_COP.format(valor),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoChip(texto: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun calcularDiasPrev(inicio: LocalDate, fin: LocalDate): Int {
    val d1 = inicio.dayOfMonth.coerceAtMost(30)
    val d2 = fin.dayOfMonth.coerceAtMost(30)
    return ((fin.year - inicio.year) * 360 +
            (fin.monthValue - inicio.monthValue) * 30 +
            (d2 - d1)).coerceAtLeast(1)
}

fun formatearPuntosMiles(texto: String): String {
    val digitos = texto.filter { it.isDigit() }
    if (digitos.isEmpty()) return ""

    val numero = digitos.toLongOrNull() ?: 0L
    return "%,d".format(numero).replace(',', '.')
}