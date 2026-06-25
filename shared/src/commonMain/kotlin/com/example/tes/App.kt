package com.example.tes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

enum class AppScreen {
    Welcome,
    Login,
    Register,
    Dashboard
}

@Composable
@Preview
fun App() {
    var currentScreen by remember { mutableStateOf(AppScreen.Welcome) }
    var userToken by remember { mutableStateOf("") }

    MaterialTheme {
        when (currentScreen) {
            AppScreen.Welcome -> {
                WelcomeScreen(
                    onNavigateToLogin = { currentScreen = AppScreen.Login },
                    onNavigateToRegister = { currentScreen = AppScreen.Register }
                )
            }
            AppScreen.Login -> {
                LoginScreen(
                    onLoginSuccess = { token ->
                        userToken = token
                        currentScreen = AppScreen.Dashboard
                    },
                    onBack = { currentScreen = AppScreen.Welcome }
                )
            }
            AppScreen.Register -> {
                RegistrationScreen(
                    onBack = { currentScreen = AppScreen.Welcome }
                )
            }
            AppScreen.Dashboard -> {
                DashboardScreen(
                    token = userToken,
                    onLogout = {
                        userToken = ""
                        currentScreen = AppScreen.Welcome
                    }
                )
            }
        }
    }
}
