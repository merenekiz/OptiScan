package com.optiscan.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ExamList : Screen("exam_list")
    object CreateExam : Screen("create_exam")
    object Camera : Screen("camera/{examId}") {
        fun createRoute(examId: Long) = "camera/$examId"
    }
    object ResultList : Screen("result_list/{examId}") {
        fun createRoute(examId: Long) = "result_list/$examId"
    }
    object Export : Screen("export/{examId}") {
        fun createRoute(examId: Long) = "export/$examId"
    }
}
