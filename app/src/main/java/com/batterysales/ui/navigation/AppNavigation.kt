package com.batterysales.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.batterysales.ui.productmanagement.ProductManagementScreen
import com.batterysales.ui.screens.*
import com.batterysales.ui.stockentry.StockEntryScreen
import com.batterysales.ui.stocktransfer.StockTransferScreen

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
        composable("product_management") { ProductManagementScreen() }
        composable(
            route = "stock_entry?entryId={entryId}",
            arguments = listOf(navArgument("entryId") {
                type = NavType.StringType
                nullable = true
            })
        ) {
            StockEntryScreen(navController = navController)
        }
        composable("stock_transfer") { StockTransferScreen() }
        composable("clients") { ClientScreen(navController) }
        composable("accounting") { AccountingScreen(navController) }
        composable("bills") { BillsScreen(navController) }
        composable("reports") { ReportsScreen(navController) }
        composable(
            route = "product_ledger/{variantId}/{productName}/{variantCapacity}",
            arguments = listOf(
                navArgument("variantId") { type = NavType.StringType },
                navArgument("productName") { type = NavType.StringType },
                navArgument("variantCapacity") { type = NavType.StringType }
            )
        ) {
            ProductLedgerScreen(navController)
        }
    }
}
