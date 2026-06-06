package com.grindbell.app.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.grindbell.app.presentation.analytics.AnalyticsScreen
import com.grindbell.app.presentation.dashboard.DashboardScreen
import com.grindbell.app.presentation.habits.AddHabitScreen
import com.grindbell.app.presentation.habits.HabitDetailScreen
import com.grindbell.app.presentation.habits.HabitsScreen
import com.grindbell.app.presentation.tasks.AddTaskScreen
import com.grindbell.app.presentation.tasks.TaskDetailScreen
import com.grindbell.app.presentation.tasks.TasksScreen
import com.grindbell.app.presentation.theme.*

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Tasks : Screen("tasks")
    data object Habits : Screen("habits")
    data object Analytics : Screen("analytics")
    data object AddTask : Screen("add_task")
    data object AddHabit : Screen("add_habit")
    data object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: Long) = "task_detail/$taskId"
    }
    data object HabitDetail : Screen("habit_detail/{habitId}") {
        fun createRoute(habitId: Long) = "habit_detail/$habitId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun GrindBellNavigation(navController: NavHostController = rememberNavController()) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavItem(Screen.Tasks, "Tasks", Icons.Filled.Checklist, Icons.Outlined.Checklist),
        BottomNavItem(Screen.Habits, "Habits", Icons.Filled.Repeat, Icons.Outlined.Repeat)
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content area
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 } }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToTasks = {
                        navController.navigate(Screen.Tasks.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToHabits = {
                        navController.navigate(Screen.Habits.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAnalytics = {
                        navController.navigate(Screen.Analytics.route)
                    },
                    onAddTask = { navController.navigate(Screen.AddTask.route) },
                    onReminderTap = { taskId, habitId ->
                        when {
                            taskId != null -> navController.navigate(Screen.TaskDetail.createRoute(taskId))
                            habitId != null -> navController.navigate(Screen.HabitDetail.createRoute(habitId))
                        }
                    },
                    onTaskClick = { taskId -> navController.navigate(Screen.TaskDetail.createRoute(taskId)) }
                )
            }

            composable(Screen.Tasks.route) {
                TasksScreen(
                    onTaskClick = { taskId -> navController.navigate(Screen.TaskDetail.createRoute(taskId)) },
                    onAddTask = { navController.navigate(Screen.AddTask.route) }
                )
            }

            composable(Screen.Habits.route) {
                HabitsScreen(
                    onHabitClick = { habitId -> navController.navigate(Screen.HabitDetail.createRoute(habitId)) },
                    onAddHabit = { navController.navigate(Screen.AddHabit.route) }
                )
            }

            composable(Screen.Analytics.route) {
                AnalyticsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.TaskDetail.route,
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getLong("taskId") ?: return@composable
                TaskDetailScreen(
                    taskId = taskId,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.HabitDetail.route,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType })
            ) { backStackEntry ->
                val habitId = backStackEntry.arguments?.getLong("habitId") ?: return@composable
                HabitDetailScreen(
                    habitId = habitId,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(Screen.AddTask.route) {
                AddTaskScreen(
                    onTaskCreated = {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                        navController.navigate(Screen.Tasks.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Screen.AddHabit.route) {
                AddHabitScreen(
                    onHabitCreated = {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                        navController.navigate(Screen.Habits.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        // ── FLOATING GLASS DOCK ──
        if (showBottomBar) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = ElectricBlue.copy(alpha = 0.06f),
                        spotColor = ElectricBlue.copy(alpha = 0.1f)
                    )
                    .clip(RoundedCornerShape(28.dp)),
                color = SurfaceCard,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        val bgColor by animateColorAsState(
                            targetValue = if (selected) ElectricBlue.copy(alpha = 0.12f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                            label = "navBg"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (selected) ElectricBlue else TextTertiary,
                            label = "navIcon"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) ElectricBlue else TextTertiary,
                            label = "navText"
                        )

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgColor)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (currentRoute != item.screen.route) {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(Screen.Dashboard.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp),
                                tint = iconColor
                            )
                            if (selected) {
                                Text(
                                    text = item.label,
                                    style = GrindBellTypography.labelSmall.copy(color = textColor),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
