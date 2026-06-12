package com.example.nomina
// Importaciones de animaciones, layouts y UI components de Jetpack Compose
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
//  Formateadores globales (Utilerías de texto)
// ─────────────────────────────────────────────────────────────────────────────
// Define el formato visual de las fechas en la UI (ej: "28/02/2026")
private val FMT_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Formatea los números decimales a moneda legal colombiana sin centavos (ej: "$ 1.750.905")
private val FMT_COP: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
    maximumFractionDigits = 0
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla principal (UI)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NominaScreen(viewModel: NominaViewModel = viewModel()) {
    // Escucha en tiempo real los cambios de estado provenientes del ViewModel
    val state by viewModel.state.collectAsState()

    // Estado local para abrir o cerrar el menú desplegable (Spinner) del tipo de contrato
    var expandedSpinner by remember { mutableStateOf(false) }

    // Estructura base de la pantalla que provee una barra superior (TopAppBar)
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
    ) { padding -> // 'padding' evita que el contenido se solape con la barra superior
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()), // Permite hacer scroll si el contenido no cabe
            verticalArrangement = Arrangement.spacedBy(14.dp) // Espaciado uniforme de 14dp entre elementos
        ) {

            // ── 1. Entrada: Salario bruto ─────────────────────────────────────────────
            OutlinedTextField(
                // Muestra el salario formateado con puntos de miles mientras el usuario escribe
                value = formatearPuntosMiles(state.salarioBruto),
                onValueChange = { nuevoTexto ->
                    // Filtra el texto para quedarse únicamente con números
                    val soloDigitos = nuevoTexto.filter { it.isDigit() }
                    // Valida que no supere los 12 dígitos para evitar errores de desbordamiento numérico
                    if (soloDigitos.length <= 12) {
                        viewModel.onSalarioChange(soloDigitos) // Envía el cambio al ViewModel
                    }
                },
                label = { Text("Salario Bruto (COP)") },
                placeholder = { Text("Ej: 2.000.000") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number // Fuerza a que el teclado del celular sea numérico
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── 2. Spinner: Selección del tipo de contrato ──────────────────────────────────
            Text("Tipo de contrato", style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(
                expanded = expandedSpinner,
                onExpandedChange = { expandedSpinner = !expandedSpinner }
            ) {
                OutlinedTextField(
                    // Muestra el texto descriptivo según el Enum actual del estado
                    value = if (state.tipoContrato == TipoContrato.NOMINA)
                        "Nómina" else "Prestación de servicios",
                    onValueChange = {},
                    readOnly = true, // Bloquea la escritura por teclado, solo se selecciona del menú
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSpinner) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                // Menú desplegable flotante
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

            // ── 3a. Opciones exclusivas de NÓMINA (Aparecen con animación suave) ────────────────────────────
            AnimatedVisibility(
                visible = state.tipoContrato == TipoContrato.NOMINA,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    HorizontalDivider() // Línea divisoria gris
                    Text(
                        "Período laborado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Selector de Fecha Inicio usando un componente personalizado (definido abajo)
                    DatePickerField(
                        label = "Fecha inicio",
                        fecha = state.fechaInicio,
                        onFechaSeleccionada = { viewModel.onFechaInicioChange(it) }
                    )

                    // Selector de Fecha Fin (Bloquea días anteriores a la fecha de inicio)
                    DatePickerField(
                        label = "Fecha fin",
                        fecha = state.fechaFin,
                        onFechaSeleccionada = { viewModel.onFechaFinChange(it) },
                        minDate = state.fechaInicio
                    )

                    // Muestra una estimación de días en vivo antes de presionar "Calcular"
                    if (!state.esCalculado) {
                        val diasPrev = calcularDiasPrev(state.fechaInicio, state.fechaFin)
                        InfoChip("Días comerciales estimados: $diasPrev / 30")
                    }

                    // Fila con el Checkbox para activar/desactivar la Prima legal
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

            // ── 3b. Opciones exclusivas de PRESTACIÓN DE SERVICIOS ────────────────────────
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

                    // Campo para editar el porcentaje del IBC (Por defecto "40")
                    OutlinedTextField(
                        value = state.porcentajeIBC,
                        onValueChange = { nuevoValor ->
                            viewModel.onPorcentajeIBCChange(nuevoValor.filter { it.isDigit() })
                        },
                        label = { Text("% del ingreso como IBC") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        suffix = { Text("%") },
                        supportingText = { Text("Mínimo legal: 40%  |  Máximo: 100%") }
                    )

                    // Mensaje informativo con las tasas de independientes por ley
                    InfoChip("Salud: 12.5% del IBC  •  Pensión: 16% del IBC (aporte contratista)")
                }
            }

            // ── 4. Botón de acción principal ─────────────────────────────────────────────
            Button(
                onClick = { viewModel.calcular() },
                modifier = Modifier.fillMaxWidth(),
                // Se deshabilita si el campo de salario está vacío o en blanco
                enabled = state.salarioBruto.isNotBlank()
            ) {
                Text("Calcular salario neto")
            }

            // ── 5. Bloque de Resultados (Solo visible cuando 'esCalculado == true') ────────────────────────────────────────────────
            AnimatedVisibility(visible = state.esCalculado) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    HorizontalDivider()

                    Text(
                        "Resumen de descuentos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Informa los días liquidados finales (solo para empleados de nómina)
                    if (state.tipoContrato == TipoContrato.NOMINA) {
                        InfoChip(
                            "Días trabajados: ${state.diasTrabajados} / 30  " +
                                    "— Proporcional: ${FMT_COP.format(state.salarioBase)}"
                        )
                    }

                    // Fila: Ingreso bruto inicial
                    FilaResultado(
                        "Ingreso bruto",
                        state.salarioBruto.toDoubleOrNull() ?: 0.0,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Fila: Suma de auxilio de transporte si aplica
                    if (state.auxilioTransporte > 0) {
                        FilaResultado(
                            "(+) Auxilio de transporte",
                            state.auxilioTransporte,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Fila: Descuento de Salud (Cambia el texto según el contrato)
                    FilaResultado(
                        if (state.tipoContrato == TipoContrato.NOMINA)
                            "(-) Salud empleado 4%"
                        else
                            "(-) Salud contratista 12.5%",
                        state.salud
                    )

                    // Fila: Descuento de Pensión básica (sin incluir el FSP)
                    FilaResultado(
                        if (state.tipoContrato == TipoContrato.NOMINA)
                            "(-) Pensión empleado 4%"
                        else
                            "(-) Pensión contratista 16%",
                        state.pension - state.fsp
                    )

                    // Fila: Muestra de forma separada el Fondo de Solidaridad si el usuario superó el tope
                    if (state.fsp > 0) {
                        FilaResultado(
                            "(-) Fondo Solidaridad Pensional (FSP)",
                            state.fsp
                        )
                    }

                    // Fila: Descuento por retención en la fuente (impuestos)
                    if (state.retencion > 0) {
                        FilaResultado("(-) Retención en la fuente", state.retencion)
                    }

                    // Fila: Suma de prima legal (si se seleccionó)
                    if (state.prima > 0) {
                        FilaResultado(
                            "(+) Prima de servicios",
                            state.prima,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    HorizontalDivider()

                    // ── Tarjeta destacada de Salario Neto Recibido ─────────────────────────────
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
                            // Muestra el valor neto final formateado en pesos colombianos de tamaño grande
                            Text(
                                FMT_COP.format(state.salarioNeto),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ── Aviso o pie de página legal ───────────────────────────────────────
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
//  Componente Reutilizable: Selector de fecha nativo (DatePickerDialog)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    fecha: LocalDate,
    onFechaSeleccionada: (LocalDate) -> Unit,
    minDate: LocalDate? = null // Fecha mínima opcional para validar límites temporales
) {
    var showDialog by remember { mutableStateOf(false) } // Controla si se despliega el calendario

    OutlinedTextField(
        value = fecha.format(FMT_FECHA),
        onValueChange = {},
        readOnly = true, // Evita la edición por teclado manual
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar $label")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    // Si se activa la bandera, pinta el cuadro de diálogo flotante con el calendario
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
                    // Deshabilita del calendario los días anteriores a 'minDate'
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
                        // Convierte los milisegundos seleccionados de vuelta a un objeto LocalDate
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
            DatePicker(state = datePickerState) // El componente visual del calendario de Material 3
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Componentes moleculares auxiliares
// ─────────────────────────────────────────────────────────────────────────────

// Dibuja un renglón simple con un nombre a la izquierda y el precio en pesos a la derecha
@Composable
private fun FilaResultado(
    label: String,
    valor: Double,
    color: Color = MaterialTheme.colorScheme.error // Rojo por defecto para denotar descuentos
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

// Caja informativa con fondo azul/púrpura suave e icono de información integrado
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

// Duplicado de la lógica matemática para previsualizar los días comerciales (base 30) en la UI
private fun calcularDiasPrev(inicio: LocalDate, fin: LocalDate): Int {
    val d1 = inicio.dayOfMonth.coerceAtMost(30)
    val d2 = fin.dayOfMonth.coerceAtMost(30)
    return ((fin.year - inicio.year) * 360 +
            (fin.monthValue - inicio.monthValue) * 30 +
            (d2 - d1)).coerceAtLeast(1)
}

// Toma un String de puros números ("2000000") y lo transforma dinámicamente a "2.000.000"
fun formatearPuntosMiles(texto: String): String {
    val digitos = texto.filter { it.isDigit() }
    if (digitos.isEmpty()) return ""

    val numero = digitos.toLongOrNull() ?: 0L
    return "%,d".format(numero).replace(',', '.')
}