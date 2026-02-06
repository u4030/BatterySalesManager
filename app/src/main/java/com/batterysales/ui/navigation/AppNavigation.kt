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
        // In I:/battery-sales-manager/app/src/main/java/com/batterysales/ui/navigation/AppNavigation.kt

        composable(
            route = "invoice_detail/{invoiceId}",
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
        ) {
            // The invoiceId from the route is now handled inside the screen
            InvoiceDetailScreen(navController = navController)
        }

        composable("warehouse") { WarehouseScreen(navController = navController) }
        composable("product_management") { ProductManagementScreen() }
        composable("settings") { SettingsScreen(navController = navController) }
        composable(
            route = "stock_entry?entryId={entryId}",
            arguments = listOf(navArgument("entryId") {
                type = NavType.StringType
                nullable = true
            })
        ) {
            StockEntryScreen(navController = navController)
        }
        composable("stock_transfer") { StockTransferScreen(navController = navController) }
        composable("clients") { ClientScreen(navController) }
        composable("accounting") { AccountingScreen(navController) }
        composable("bank") { BankScreen(navController) }
        composable("old_battery_ledger") { OldBatteryLedgerScreen(navController) }
        composable("bills") { BillsScreen(navController) }
        composable("reports") { ReportsScreen(navController) }
        composable("user_management") { UserManagementScreen(navController) }
        composable("approvals") { ApprovalsScreen(navController) }
        composable("suppliers") { SupplierManagementScreen(navController) }
        composable(
            route = "product_ledger/{variantId}/{productName}/{variantCapacity}/{variantSpecification}",
            arguments = listOf(
                navArgument("variantId") { type = NavType.StringType },
                navArgument("productName") { type = NavType.StringType },
                navArgument("variantCapacity") { type = NavType.StringType },
                navArgument("variantSpecification") { type = NavType.StringType }
            )
        ) {
            ProductLedgerScreen(navController)
        }
    }
}
