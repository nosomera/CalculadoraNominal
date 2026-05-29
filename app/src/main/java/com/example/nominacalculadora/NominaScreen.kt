package com.example.nomina

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NominaScreen(viewModel: NominaViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val formatoCOP = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    // Estado del Spinner (DropdownMenu)
    var expandedSpinner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora de Nómina") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Campo: Salario Bruto ──
            OutlinedTextField(
                value = state.salarioBruto,
                onValueChange = { viewModel.onSalarioChange(it) },
                label = { Text("Salario Bruto (COP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("$") }
            )

            // ── Spinner: Tipo de Contrato ──
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

            // ── Botón Calcular ──
            Button(
                onClick = { viewModel.calcular() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.salarioBruto.isNotBlank()
            ) {
                Text("Calcular salario neto")
            }

            // ── Resultados ──
            if (state.calculado) {
                HorizontalDivider()

                Text(
                    "Resumen de descuentos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                DeduccionItem("Salario bruto", state.salarioBruto.toDouble(), formatoCOP, color = MaterialTheme.colorScheme.onSurface)
                DeduccionItem("(-) Salud (4%)", state.salud, formatoCOP)
                DeduccionItem("(-) Pensión (4%)", state.pension, formatoCOP)
                if (state.retencion > 0) {
                    DeduccionItem("(-) Retención en la fuente", state.retencion, formatoCOP)
                }

                HorizontalDivider()

                // Salario neto destacado
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Salario neto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            formatoCOP.format(state.salarioNeto),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeduccionItem(
    label: String,
    valor: Double,
    formato: NumberFormat,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            formato.format(valor),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}