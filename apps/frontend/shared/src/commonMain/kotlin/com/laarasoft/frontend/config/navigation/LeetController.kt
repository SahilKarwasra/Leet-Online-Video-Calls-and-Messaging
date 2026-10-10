package com.laarasoft.frontend.config.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

@Stable
@Composable
fun rememberLeetController(
    navController: NavHostController = rememberNavController(),
): LeetController = remember(navController) {
    LeetController(navController)
}

@Stable
class LeetController(
    val navController: NavHostController
) {

    @Stable
    fun upPress() {
        navController.navigateUp()
    }

    @Stable
    fun navigate(route: Any) {
        navController.navigate(route)
    }

    fun navigateWithPOP(route: Any, popUpTo: Any) {
        navController.navigate(route) {
            popUpTo(popUpTo) {
                inclusive = true
            }
        }
    }

    @Stable
    fun navigateToTop(route: Any) {
        navController.navigate(route) {
            popUpTo(0) {
                inclusive = true
            }
        }
    }
}