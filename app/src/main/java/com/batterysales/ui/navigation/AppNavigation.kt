package com.batterysales.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.batterysales.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") { LoginScreen(navController) }
        composable("dashboard") { DashboardScreen(navController) }
        composable("sales") { SalesScreen(navController) }
        composable("invoices") { InvoiceScreen(navController) }
        composable(
            route = "invoice_detail/{invoiceId}",
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getString("invoiceId") ?: ""
            InvoiceDetailScreen(navController, invoiceId)
        }
        composable("warehouse") { WarehouseScreen(navController = navController) }
        composable("clients") { ClientScreen(navController) }
        composable("accounting") { AccountingScreen(navController) }
        composable("bills") { BillsScreen(navController) }
        composable("reports") { ReportsScreen(navController) }
    }
}
