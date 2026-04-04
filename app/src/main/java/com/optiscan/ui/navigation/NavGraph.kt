package com.optiscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.optiscan.ui.screens.camera.CameraScreen
import com.optiscan.ui.screens.exams.CreateExamScreen
import com.optiscan.ui.screens.exams.ExamListScreen
import com.optiscan.ui.screens.export.ExportScreen
import com.optiscan.ui.screens.home.HomeScreen
import com.optiscan.ui.screens.results.ResultsScreen

@Composable
fun OptiScanNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToExams = { navController.navigate(Screen.ExamList.route) },
                onNavigateToScan = {
                    // Quick scan — navigate to exam list to pick exam
                    navController.navigate(Screen.ExamList.route)
                }
            )
        }

        composable(Screen.ExamList.route) {
            ExamListScreen(
                onNavigateToCreate = { navController.navigate(Screen.CreateExam.route) },
                onNavigateToExam = { examId ->
                    navController.navigate(Screen.ResultList.createRoute(examId))
                },
                onNavigateToCamera = { examId ->
                    navController.navigate(Screen.Camera.createRoute(examId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CreateExam.route) {
            CreateExamScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Camera.route,
            arguments = listOf(navArgument("examId") { type = NavType.LongType })
        ) { backStack ->
            val examId = backStack.arguments?.getLong("examId") ?: return@composable
            CameraScreen(
                examId = examId,
                onBack = { navController.popBackStack() },
                onResultSaved = { resultId ->
                    navController.navigate(Screen.ResultList.createRoute(examId))
                }
            )
        }

        composable(
            Screen.ResultList.route,
            arguments = listOf(navArgument("examId") { type = NavType.LongType })
        ) { backStack ->
            val examId = backStack.arguments?.getLong("examId") ?: return@composable
            ResultsScreen(
                examId = examId,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(Screen.Export.createRoute(examId)) }
            )
        }

        composable(
            Screen.Export.route,
            arguments = listOf(navArgument("examId") { type = NavType.LongType })
        ) { backStack ->
            val examId = backStack.arguments?.getLong("examId") ?: return@composable
            ExportScreen(
                examId = examId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
