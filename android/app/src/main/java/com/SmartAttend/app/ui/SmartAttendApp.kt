package com.SmartAttend.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.SmartAttend.app.BuildConfig
import com.SmartAttend.app.R
import com.SmartAttend.app.data.ActiveSession
import com.SmartAttend.app.data.AdminAttendanceAnalytics
import com.SmartAttend.app.data.AdminAttendanceClassAnalytics
import com.SmartAttend.app.data.AdminClassOverview
import com.SmartAttend.app.data.AdminClassDetail
import com.SmartAttend.app.data.AdminDashboardData
import com.SmartAttend.app.data.AdminLiveSessionOverview
import com.SmartAttend.app.data.AdminUserOverview
import com.SmartAttend.app.data.BiometricProfile
import com.SmartAttend.app.data.ClassStudentGroup
import com.SmartAttend.app.data.EnrollableClass
import com.SmartAttend.app.data.FacultyAttendanceDay
import com.SmartAttend.app.data.FacultyClassOverview
import com.SmartAttend.app.data.StudentAttendanceEntry
import com.SmartAttend.app.data.StudentClassOverview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.SmartAttend.app.ui.theme.Danger
import com.SmartAttend.app.ui.theme.PanelRaised
import com.SmartAttend.app.ui.theme.Success
import com.SmartAttend.app.ui.theme.Warm

private data class FaceFlow(
    val sessionId: String?,
    val registrationOnly: Boolean
)

private data class FacultySessionDraft(
    val classId: String,
    val className: String,
    val subject: String
)

private data class AttendanceSummary(
    val total: Int,
    val present: Int,
    val percent: Int
)

private data class StudentAttendanceSection(
    val key: String,
    val className: String,
    val subject: String,
    val summary: AttendanceSummary,
    val entries: List<StudentAttendanceEntry>
)

private sealed interface StudentAttendanceRow {
    data class Section(val section: StudentAttendanceSection) : StudentAttendanceRow
    data class Entry(
        val rowId: String,
        val status: String,
        val date: String,
        val method: String?
    ) : StudentAttendanceRow
}

@Composable
fun SmartAttendApp(
    viewModel: AppViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var showQrScanner by remember { mutableStateOf(false) }
    var faceFlow by remember { mutableStateOf<FaceFlow?>(null) }
    var facultyDraft by remember { mutableStateOf<FacultySessionDraft?>(null) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var facultyFaceAttendanceFlow by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var pendingProfileFaceRegistration by remember { mutableStateOf(false) }
    var showFacultyCalendar by remember { mutableStateOf(false) }
    var showStudentAttendance by remember { mutableStateOf(false) }
    var showAdminCreateClass by remember { mutableStateOf(false) }
    var showAdminAnalytics by remember { mutableStateOf(false) }
    var showAdminCreateAccount by remember { mutableStateOf(false) }
    var appVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appVisible = true
    }

    LaunchedEffect(uiState.currentRole, uiState.profile?.uid) {
        if (uiState.currentRole == null || uiState.profile == null) return@LaunchedEffect
        while (true) {
            delay(5000)
            val dialogOpen = showQrScanner ||
                faceFlow != null ||
                facultyDraft != null ||
                showEnrollDialog ||
                facultyFaceAttendanceFlow ||
                showProfileDialog ||
                showFacultyCalendar ||
                showStudentAttendance ||
                showAdminCreateClass ||
                showAdminAnalytics ||
                showAdminCreateAccount ||
                uiState.adminSelectedClassDetail != null
            val allowAutoRefresh = uiState.currentRole == "student" || uiState.currentRole == "faculty"
            if (allowAutoRefresh && !dialogOpen) {
                viewModel.refreshCurrentData(lightweight = true)
            }
        }
    }

    LaunchedEffect(pendingProfileFaceRegistration, showProfileDialog) {
        if (pendingProfileFaceRegistration && !showProfileDialog) {
            yield()
            faceFlow = FaceFlow(sessionId = null, registrationOnly = true)
            pendingProfileFaceRegistration = false
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(message) }
        viewModel.clearStatusMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        AnimatedVisibility(
            visible = appVisible,
            enter = fadeIn(animationSpec = tween(420, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { it / 14 }, animationSpec = tween(420, easing = FastOutSlowInEasing))
        ) {
            when {
                uiState.loading -> LoadingScreen(padding)
                uiState.currentRole == null -> LoginScreen(
                    padding = padding,
                    error = uiState.loginError,
                    onLogin = viewModel::login
                )
                uiState.currentRole == "student" -> StudentDashboardScreen(
                    padding = padding,
                    name = uiState.profile?.name.orEmpty(),
                    biometricProfile = uiState.biometricProfile,
                    classes = uiState.studentClasses,
                    attendanceHistory = uiState.studentAttendanceHistory,
                    onLogout = viewModel::logout,
                    onOpenProfile = { showProfileDialog = true },
                    onOpenEnroll = {
                        showEnrollDialog = true
                        viewModel.loadEnrollableClasses()
                    },
                    onOpenAttendance = { showStudentAttendance = true },
                    onOpenQrScanner = { showQrScanner = true },
                    onRegisterFingerprint = {
                        if (activity == null) {
                            scope.launch { snackbarHostState.showSnackbar("Biometric setup requires a FragmentActivity context.") }
                        } else {
                            val helper = BiometricPromptHelper(activity)
                            helper.authenticate(
                                title = "Register Fingerprint",
                                subtitle = "Confirm device biometrics to save fingerprint setup for this account.",
                                onSuccess = {
                                    viewModel.registerFingerprint(android.os.Build.MODEL ?: "Android device")
                                },
                                onError = { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            )
                        }
                    },
                    onRegisterFace = { faceFlow = FaceFlow(sessionId = null, registrationOnly = true) },
                    onFingerprintAttendance = { sessionId ->
                        if (activity == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Biometric prompt requires a FragmentActivity context.")
                            }
                        } else {
                            val helper = BiometricPromptHelper(activity)
                            if (!helper.canAuthenticate()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("This device has no biometric credential configured.")
                                }
                            } else {
                                helper.authenticate(
                                    title = "Student Attendance Verification",
                                    subtitle = "Use your fingerprint or device biometric to mark attendance.",
                                    onSuccess = { viewModel.markFingerprintAttendance(sessionId) },
                                    onError = { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                )
                            }
                        }
                    },
                    onFaceAttendance = { sessionId ->
                        faceFlow = FaceFlow(sessionId = sessionId, registrationOnly = false)
                    },
                    onQrAttendance = { showQrScanner = true }
                )
                uiState.currentRole == "faculty" -> FacultyDashboardScreen(
                    padding = padding,
                    name = uiState.profile?.name.orEmpty(),
                    classes = uiState.facultyClasses,
                    studentGroups = uiState.facultyStudentGroups,
                    biometricProfile = uiState.biometricProfile,
                    attendanceMethodsToday = uiState.facultyAttendanceMethodsToday,
                    attendanceCalendar = uiState.facultyAttendanceCalendar,
                    onOpenProfile = { showProfileDialog = true },
                    onOpenCalendar = {
                        showFacultyCalendar = true
                        viewModel.loadFacultyAttendanceCalendar()
                    },
                    onLaunchSession = { item ->
                        facultyDraft = FacultySessionDraft(item.id, item.name, item.subject)
                    },
                    onEndClass = viewModel::endFacultyClass,
                    onMarkFingerprintAttendance = {
                        if (activity == null) {
                            scope.launch { snackbarHostState.showSnackbar("Biometric prompt requires a FragmentActivity context.") }
                        } else {
                            val helper = BiometricPromptHelper(activity)
                            helper.authenticate(
                                title = "Faculty Attendance",
                                subtitle = "Confirm your device biometric to unlock class access for today.",
                                onSuccess = { viewModel.markFacultyAttendance("fingerprint") },
                                onError = { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            )
                        }
                    },
                    onMarkFaceAttendance = { facultyFaceAttendanceFlow = true }
                )
                else -> AdminDashboardScreen(
                    padding = padding,
                    name = uiState.profile?.name.orEmpty(),
                    biometricProfile = uiState.biometricProfile,
                    dashboard = uiState.adminDashboard,
                    studentGroups = uiState.adminStudentGroups,
                    onOpenProfile = { showProfileDialog = true },
                    onCreateClass = { showAdminCreateClass = true },
                    onOpenAnalytics = {
                        showAdminAnalytics = true
                        viewModel.loadAdminAttendanceAnalytics()
                    },
                    onOpenCreateAccount = { showAdminCreateAccount = true },
                    onManageClass = { classId -> viewModel.loadAdminClassDetail(classId) },
                    onResetPassword = viewModel::adminSendPasswordReset,
                    onDeleteAccount = viewModel::deleteAdminManagedAccount,
                    onLogout = viewModel::logout
                )
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrScanned = { rawValue ->
                showQrScanner = false
                viewModel.markQrAttendance(rawValue)
            }
        )
    }

    val activeFaceFlow = faceFlow
    if (activeFaceFlow != null) {
        FaceCaptureDialog(
            title = if (activeFaceFlow.registrationOnly) "Register Face Scan" else "Face Attendance",
            body = if (activeFaceFlow.registrationOnly) {
                "Use the front camera once to register this account for face attendance on Android."
            } else {
                "Use the front camera, wait for face detection, then capture to mark attendance."
            },
            actionLabel = if (activeFaceFlow.registrationOnly) "Register Face" else "Capture & Mark",
            onDismiss = { faceFlow = null },
            onCapture = { snapshot ->
                faceFlow = null
                if (activeFaceFlow.registrationOnly) {
                    viewModel.registerFace(snapshot.width, snapshot.height, snapshot.photoBase64, snapshot.signature)
                } else {
                    viewModel.markFaceAttendance(activeFaceFlow.sessionId.orEmpty(), snapshot.width, snapshot.height, snapshot.signature)
                }
            }
        )
    }

    val activeFacultyDraft = facultyDraft
    if (activeFacultyDraft != null) {
        SessionLaunchDialog(
            className = activeFacultyDraft.className,
            subject = activeFacultyDraft.subject,
            onDismiss = { facultyDraft = null },
            onLaunch = { method, duration ->
                facultyDraft = null
                viewModel.launchFacultySession(activeFacultyDraft.classId, method, duration)
            }
        )
    }

    if (facultyFaceAttendanceFlow) {
        FaceCaptureDialog(
            title = "Faculty Face Attendance",
            body = "Use the front camera to mark your faculty attendance for today.",
            actionLabel = "Mark Attendance",
            onDismiss = { facultyFaceAttendanceFlow = false },
            onCapture = { snapshot ->
                facultyFaceAttendanceFlow = false
                viewModel.markFacultyAttendance(
                    "face",
                    snapshot.width,
                    snapshot.height,
                    snapshot.photoBase64,
                    snapshot.signature
                )
            }
        )
    }

    if (showEnrollDialog) {
        EnrollClassesDialog(
            classes = uiState.enrollableClasses,
            onDismiss = { showEnrollDialog = false },
            onEnroll = viewModel::enrollInClass
        )
    }

    if (showProfileDialog && uiState.profile != null) {
        ProfileDialog(
            profile = uiState.profile!!,
            biometricProfile = uiState.biometricProfile,
            assignedBadges = when (uiState.profile?.role) {
                "student" -> uiState.studentClasses.map { it.name }
                "faculty" -> uiState.facultyClasses.map { it.name }
                else -> emptyList()
            },
            onDismiss = { showProfileDialog = false },
            onReRegisterFingerprint = {
                showProfileDialog = false
                if (activity != null) {
                    BiometricPromptHelper(activity).authenticate(
                        title = "Re-register Fingerprint",
                        subtitle = "Confirm device biometrics to refresh fingerprint setup.",
                        onSuccess = { viewModel.registerFingerprint(android.os.Build.MODEL ?: "Android device") },
                        onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                    )
                }
            },
            onReRegisterFace = {
                showProfileDialog = false
                pendingProfileFaceRegistration = true
            },
            onLogout = viewModel::logout
        )
    }

    if (showFacultyCalendar) {
        FacultyAttendanceCalendarDialog(
            days = uiState.facultyAttendanceCalendar,
            onDismiss = { showFacultyCalendar = false }
        )
    }

    if (showStudentAttendance) {
        val overallSummary = remember(uiState.studentAttendanceHistory) {
            val total = uiState.studentAttendanceHistory.size
            val present = uiState.studentAttendanceHistory.count { it.status.equals("present", ignoreCase = true) }
            AttendanceSummary(total, present, if (total == 0) 0 else ((present * 100f) / total).toInt())
        }
        StudentAttendanceDialog(
            entries = uiState.studentAttendanceHistory,
            overallSummary = overallSummary,
            onDismiss = { showStudentAttendance = false }
        )
    }

    if (showAdminCreateClass) {
        AdminCreateClassDialog(
            onDismiss = { showAdminCreateClass = false },
            onCreate = { name ->
                showAdminCreateClass = false
                viewModel.createAdminClass(name)
            }
        )
    }

    if (showAdminAnalytics) {
        AdminAttendanceAnalyticsDialog(
            analytics = uiState.adminAttendanceAnalytics,
            onDismiss = { showAdminAnalytics = false }
        )
    }

    if (showAdminCreateAccount) {
        AdminCreateAccountDialog(
            availableClasses = uiState.adminDashboard.classes,
            onDismiss = { showAdminCreateAccount = false },
            onCreate = { name, email, password, role, rollNo, classIds ->
                showAdminCreateAccount = false
                viewModel.createAdminAccount(
                    context = context.applicationContext,
                    name = name,
                    email = email,
                    password = password,
                    role = role,
                    rollNo = rollNo,
                    classIds = classIds
                )
            }
        )
    }

    uiState.adminSelectedClassDetail?.let { detail ->
        AdminManageClassDialog(
            detail = detail,
            facultyUsers = uiState.adminDashboard.users.filter { it.role == "faculty" },
            onDismiss = viewModel::clearAdminClassDetail,
            onAssignStudent = { studentId -> viewModel.adminAssignStudentToClass(detail.classId, studentId) },
            onUpdateSubject = { subject, facultyId -> viewModel.updateAdminClassDetails(detail.classId, subject, facultyId) },
            onDeleteClass = { viewModel.deleteAdminClass(detail.classId) }
        )
    }
}

@Composable
private fun LoadingScreen(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoginScreen(
    padding: PaddingValues,
    error: String?,
    onLogin: (String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Student") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PanelRaised),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "SmartAttend logo",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text("SmartAttend", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("$selectedRole access portal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    "Attendance, biometrics, QR sessions, and faculty controls in one native Android flow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Student", "Faculty", "Admin").forEach { role ->
                        val selected = selectedRole == role
                        Button(
                            onClick = { selectedRole = role },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(role)
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    }
                )
                Button(
                    onClick = { onLogin(email, password, selectedRole.lowercase()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Sign In")
                }
                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StudentDashboardScreen(
    padding: PaddingValues,
    name: String,
    biometricProfile: BiometricProfile,
    classes: List<StudentClassOverview>,
    attendanceHistory: List<StudentAttendanceEntry>,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenEnroll: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onRegisterFingerprint: () -> Unit,
    onRegisterFace: () -> Unit,
    onFingerprintAttendance: (String) -> Unit,
    onFaceAttendance: (String) -> Unit,
    onQrAttendance: (String) -> Unit
) {
    val faceRegistered = !biometricProfile.face?.registeredAt.isNullOrBlank()
    val fingerprintRegistered = !biometricProfile.fingerprint?.registeredAt.isNullOrBlank()
    val attendanceSummaryByClass = remember(attendanceHistory) {
        attendanceHistory.groupBy { it.className to it.subject }.mapValues { (_, entries) ->
            val total = entries.size
            val present = entries.count { it.status.equals("present", ignoreCase = true) }
            AttendanceSummary(total, present, if (total == 0) 0 else ((present * 100f) / total).toInt())
        }
    }
    val overallSummary = remember(attendanceHistory) {
        val total = attendanceHistory.size
        val present = attendanceHistory.count { it.status.equals("present", ignoreCase = true) }
        AttendanceSummary(total, present, if (total == 0) 0 else ((present * 100f) / total).toInt())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RevealCard(delayMillis = 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Student Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Welcome, $name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        ProfileAvatar(photoBase64 = biometricProfile.face?.photoBase64, onClick = onOpenProfile)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onOpenAttendance) { Text("Attendance (${attendanceHistory.size})") }
                        TextButton(onClick = onOpenEnroll) { Text("Enroll Classes") }
                    }
                    AttendanceProgress(summary = overallSummary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(title = "Classes", value = classes.size.toString(), modifier = Modifier.weight(1f), delayMillis = 60)
                MetricCard(title = "Live", value = classes.count { it.activeSession != null }.toString(), modifier = Modifier.weight(1f), delayMillis = 110)
                MetricCard(title = "Records", value = attendanceHistory.size.toString(), modifier = Modifier.weight(1f), delayMillis = 160)
            }
        }

        item {
            RevealCard(delayMillis = 180) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelRaised),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Biometric Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (fingerprintRegistered) "Fingerprint setup completed." else "Register fingerprint once before using fingerprint attendance.")
                        if (!fingerprintRegistered) {
                            OutlinedButton(onClick = onRegisterFingerprint, modifier = Modifier.fillMaxWidth()) {
                                Text("Setup Fingerprint")
                            }
                        }
                        Text(if (faceRegistered) "Face scan registered on this account." else "Register face scan once before using face attendance.")
                        if (!faceRegistered) {
                            OutlinedButton(onClick = onRegisterFace, modifier = Modifier.fillMaxWidth()) {
                                Text("Register Face Scan")
                            }
                        }
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No classes enrolled",
                    body = "Use Enroll Classes to join a class before attendance options appear here."
                )
            }
        } else {
            items(classes, key = { it.id }) { classItem ->
                RevealCard(delayMillis = 220) {
                    StudentClassCard(
                        classItem = classItem,
                        summary = attendanceSummaryByClass[classItem.name to classItem.subject] ?: AttendanceSummary(0, 0, 0),
                        faceRegistered = faceRegistered,
                        fingerprintRegistered = fingerprintRegistered,
                        onQrAttendance = onQrAttendance,
                        onFingerprintAttendance = onFingerprintAttendance,
                        onFaceAttendance = onFaceAttendance
                    )
                }
            }
        }

        item {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StudentClassCard(
    classItem: StudentClassOverview,
    summary: AttendanceSummary,
    faceRegistered: Boolean,
    fingerprintRegistered: Boolean,
    onQrAttendance: (String) -> Unit,
    onFingerprintAttendance: (String) -> Unit,
    onFaceAttendance: (String) -> Unit
) {
    val session = classItem.activeSession
    val allowsFingerprint = session != null && ("fingerprint" in session.methods || "all" in session.methods)
    val allowsFace = session != null && ("face" in session.methods || "all" in session.methods)
    val allowsQr = session != null && ("qr" in session.methods || "all" in session.methods)

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(classItem.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(classItem.subject, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Faculty: ${classItem.facultyName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(
                    label = "${summary.percent}%",
                    color = when {
                        summary.percent >= 75 -> Success
                        summary.percent >= 50 -> Warm
                        else -> Danger
                    }
                )
            }
            ClassStatusBadge(active = classItem.classActive)
            AttendanceProgress(summary = summary)
            if (session != null) {
                Text("Allowed methods: ${session.methods.joinToString()}")
                Text("Expires: ${session.expiresAt}")
            }
            if (classItem.activeSessionMarked) {
                Text(
                    "Attendance already marked for the current live session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            if (session != null && !classItem.activeSessionMarked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onQrAttendance(session.id) },
                        modifier = Modifier.weight(1f),
                        enabled = allowsQr
                    ) {
                        Text("QR Scanner")
                    }
                    Button(
                        onClick = { onFingerprintAttendance(session.id) },
                        modifier = Modifier.weight(1f),
                        enabled = allowsFingerprint && fingerprintRegistered
                    ) {
                        Text("Fingerprint")
                    }
                    Button(
                        onClick = { onFaceAttendance(session.id) },
                        modifier = Modifier.weight(1f),
                        enabled = allowsFace && faceRegistered
                    ) {
                        Text("Face Scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun FacultyDashboardScreen(
    padding: PaddingValues,
    name: String,
    classes: List<FacultyClassOverview>,
    studentGroups: List<ClassStudentGroup>,
    biometricProfile: BiometricProfile,
    attendanceMethodsToday: Set<String>,
    attendanceCalendar: List<FacultyAttendanceDay>,
    onOpenProfile: () -> Unit,
    onOpenCalendar: () -> Unit,
    onLaunchSession: (FacultyClassOverview) -> Unit,
    onEndClass: (String) -> Unit,
    onMarkFingerprintAttendance: () -> Unit,
    onMarkFaceAttendance: () -> Unit
) {
    val classesUnlocked = remember(attendanceMethodsToday) {
        "fingerprint" in attendanceMethodsToday && "face" in attendanceMethodsToday
    }
    var viewTab by remember { mutableStateOf("classes") }
    var classFilter by remember { mutableStateOf("all") }
    val filteredClasses = remember(classes, classFilter) {
        when (classFilter) {
            "active" -> classes.filter { it.classActive || it.activeSession != null }
            "ended" -> classes.filter { !it.classActive && it.activeSession == null }
            else -> classes
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Faculty Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Welcome, $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAvatar(photoBase64 = biometricProfile.face?.photoBase64, onClick = onOpenProfile)
                    TextButton(onClick = onOpenCalendar) { Text("Calendar") }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(title = "Assigned", value = classes.size.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "Live", value = classes.count { it.activeSession != null }.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "Status", value = if (classesUnlocked) "Ready" else "Locked", modifier = Modifier.weight(1f))
            }
        }

        item {
            if (classesUnlocked) {
                EmptyStateCard(
                    title = "Faculty Attendance",
                    body = "Attendance marked for today. Class controls are unlocked."
                )
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Faculty Attendance Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val methodsToday = attendanceCalendar.firstOrNull { it.date == LocalDate.now().toString() }?.status
                        Text(
                            if (methodsToday == "partial") {
                                "One attendance method is recorded. Mark the second method to unlock class controls."
                            } else {
                                "Mark your daily attendance before class controls are unlocked."
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                label = if ("fingerprint" in attendanceMethodsToday) "Fingerprint Done" else "Fingerprint Pending",
                                color = if ("fingerprint" in attendanceMethodsToday) Success else Danger
                            )
                            StatusPill(
                                label = if ("face" in attendanceMethodsToday) "Face Done" else "Face Pending",
                                color = if ("face" in attendanceMethodsToday) Success else Danger
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onMarkFingerprintAttendance,
                                modifier = Modifier.weight(1f),
                                enabled = "fingerprint" !in attendanceMethodsToday
                            ) { Text("Fingerprint") }
                            Button(
                                onClick = onMarkFaceAttendance,
                                modifier = Modifier.weight(1f),
                                enabled = "face" !in attendanceMethodsToday
                            ) { Text("Face Scan") }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewTab = "classes" }, modifier = Modifier.weight(1f)) { Text("Classes") }
                OutlinedButton(onClick = { viewTab = "students" }, modifier = Modifier.weight(1f)) { Text("Students") }
            }
        }

        if (!classesUnlocked) {
            item {
                EmptyStateCard(
                    title = "Classes Locked",
                    body = "Mark both fingerprint and face attendance to access class controls."
                )
            }
        } else if (viewTab == "classes" && classes.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No classes assigned",
                    body = "This faculty account does not have any classes assigned in Firebase yet."
                )
            }
        } else if (viewTab == "classes") {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { classFilter = "all" }, modifier = Modifier.weight(1f)) { Text("All") }
                    OutlinedButton(onClick = { classFilter = "active" }, modifier = Modifier.weight(1f)) { Text("Active") }
                    OutlinedButton(onClick = { classFilter = "ended" }, modifier = Modifier.weight(1f)) { Text("Ended") }
                }
            }
            items(filteredClasses, key = { it.id }) { item ->
                FacultyClassCard(
                    item = item,
                    onLaunchSession = { onLaunchSession(item) },
                    onEndClass = { onEndClass(item.id) }
                )
            }
        } else if (studentGroups.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No students yet",
                    body = "Student rosters will appear here after enrollments are added to your classes."
                )
            }
        } else {
            items(studentGroups, key = { it.classId }) { group ->
                FacultyStudentGroupCard(group = group)
            }
        }

        item {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdminDashboardScreen(
    padding: PaddingValues,
    name: String,
    biometricProfile: BiometricProfile,
    dashboard: AdminDashboardData,
    studentGroups: List<ClassStudentGroup>,
    onOpenProfile: () -> Unit,
    onCreateClass: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenCreateAccount: () -> Unit,
    onManageClass: (String) -> Unit,
    onResetPassword: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onLogout: () -> Unit
) {
    val studentCount = remember(dashboard.users) { dashboard.users.count { it.role == "student" } }
    val facultyCount = remember(dashboard.users) { dashboard.users.count { it.role == "faculty" } }
    var classTab by remember { mutableStateOf("all") }
    var userTab by remember { mutableStateOf("student") }
    val filteredClasses = remember(dashboard.classes, classTab) {
        when (classTab) {
            "active" -> dashboard.classes.filter { it.classActive }
            "ended" -> dashboard.classes.filter { !it.classActive }
            else -> dashboard.classes
        }
    }
    val filteredUsers = remember(dashboard.users, userTab) {
        when (userTab) {
            "faculty" -> dashboard.users.filter { it.role == "faculty" }
            "admin" -> dashboard.users.filter { it.role == "admin" }
            "student" -> dashboard.users.filter { it.role == "student" }
            else -> dashboard.users
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Admin Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Welcome, $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photoBase64 = biometricProfile.face?.photoBase64, onClick = onOpenProfile)
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(title = "Students", value = studentCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "Faculty", value = facultyCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "Classes", value = dashboard.classes.size.toString(), modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(title = "Live Sessions", value = dashboard.liveSessions.size.toString(), modifier = Modifier.weight(1f))
                MetricCard(title = "Today's Marks", value = dashboard.todayAttendanceCount.toString(), modifier = Modifier.weight(1f))
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text("Class Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Create classes and review active sessions, users, analytics, and enrollment load.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onOpenAnalytics, modifier = Modifier.weight(1f)) { Text("Analytics") }
                        OutlinedButton(onClick = onOpenCreateAccount, modifier = Modifier.weight(1f)) { Text("Create Account") }
                    }
                    Button(
                        onClick = onCreateClass,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Class")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Live Sessions", caption = "${dashboard.liveSessions.size} currently running")
        }

        if (dashboard.liveSessions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No live sessions",
                    body = "Faculty sessions will appear here whenever a class is started."
                )
            }
        } else {
            items(dashboard.liveSessions, key = { "live-${it.id}" }) { session ->
                AdminLiveSessionCard(session = session)
            }
        }

        item {
            SectionHeader(title = "Classes", caption = "${dashboard.classes.size} total classes")
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { classTab = "all" }, modifier = Modifier.weight(1f)) { Text("All") }
                OutlinedButton(onClick = { classTab = "active" }, modifier = Modifier.weight(1f)) { Text("Active") }
                OutlinedButton(onClick = { classTab = "ended" }, modifier = Modifier.weight(1f)) { Text("Ended") }
            }
        }

        if (filteredClasses.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No classes yet",
                    body = "Create the first class from the admin panel to start assigning students and faculty."
                )
            }
        } else {
            items(filteredClasses, key = { "class-${it.id}" }) { item ->
                AdminClassCard(item = item, onManage = { onManageClass(item.id) })
            }
        }

        item {
            SectionHeader(title = "Users", caption = "${dashboard.users.size} total accounts")
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { userTab = "student" }, modifier = Modifier.weight(1f)) { Text("Students") }
                OutlinedButton(onClick = { userTab = "faculty" }, modifier = Modifier.weight(1f)) { Text("Faculty") }
                OutlinedButton(onClick = { userTab = "admin" }, modifier = Modifier.weight(1f)) { Text("Admins") }
            }
        }

        if (userTab == "student" && studentGroups.isNotEmpty()) {
            items(studentGroups, key = { "student-group-${it.classId}" }) { group ->
                FacultyStudentGroupCard(
                    group = group,
                    onResetPassword = onResetPassword,
                    onDeleteStudent = onDeleteAccount
                )
            }
        } else if (filteredUsers.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No users found",
                    body = "User accounts from Firebase will appear here automatically."
                )
            }
        } else {
            items(filteredUsers, key = { "user-${itemRoleKey(it)}-${it.id}" }) { item ->
                AdminUserCard(
                    item = item,
                    onResetPassword = { onResetPassword(item.email) },
                    onDeleteAccount = { onDeleteAccount(item.id) }
                )
            }
        }

        item {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FacultyClassCard(
    item: FacultyClassOverview,
    onLaunchSession: () -> Unit,
    onEndClass: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(item.subject, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ClassStatusBadge(active = item.classActive)

            val session = item.activeSession
            if (session != null) {
                Text("Session live until ${session.expiresAt}")
                Text("Methods: ${session.methods.joinToString()}")
                if ("qr" in session.methods && !session.token.isNullOrBlank()) {
                    val scanUrl = "https://minegameing50.github.io/SmartAttend/scan.html?token=${session.token}"
                    val qrBitmap = rememberQrCodeBitmap(scanUrl)
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Session QR",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(4.dp, Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )
                }
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onLaunchSession,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (!item.classActive && session == null) "Start Class" else if (session == null) "Launch Session" else "Relaunch Session")
            }
            if (item.classActive || session != null) {
                OutlinedButton(
                    onClick = onEndClass,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("End Class")
                }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    caption: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun itemRoleKey(item: AdminUserOverview): String = item.role.ifBlank { "unknown" }

@Composable
private fun AdminLiveSessionCard(session: AdminLiveSessionOverview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(session.className, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(session.subject, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = session.facultyName.ifBlank { "Faculty" }, color = Warm)
                StatusPill(label = session.methods.joinToString().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, color = Success)
            }
            Text("Runs until ${session.expiresAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminClassCard(
    item: AdminClassOverview,
    onManage: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(item.subject, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = item.facultyName.ifBlank { "Faculty not linked" }, color = Warm)
                StatusPill(label = "${item.studentCount} Students", color = Success)
                StatusPill(label = if (item.classActive) "Active" else "Ended", color = if (item.classActive) Success else Danger)
            }
            OutlinedButton(onClick = onManage, shape = RoundedCornerShape(14.dp)) {
                Text("Manage Class")
            }
        }
    }
}

@Composable
private fun AdminUserCard(
    item: AdminUserOverview,
    onResetPassword: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name.ifBlank { "Unnamed user" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.role == "student" && item.rollNo.isNotBlank()) {
                        Text("Roll No: ${item.rollNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusPill(
                    label = item.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    color = when (item.role) {
                        "admin" -> Danger
                        "faculty" -> Warm
                        else -> Success
                    }
                )
            }
            if (item.role != "admin") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onResetPassword, shape = RoundedCornerShape(14.dp)) {
                        Text("Reset Password")
                    }
                    OutlinedButton(onClick = onDeleteAccount, shape = RoundedCornerShape(14.dp)) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun FacultyStudentGroupCard(
    group: ClassStudentGroup,
    onResetPassword: ((String) -> Unit)? = null,
    onDeleteStudent: ((String) -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(group.className, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(group.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (group.students.isEmpty()) {
                Text("No students enrolled yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                group.students.forEach { student ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(student.name, fontWeight = FontWeight.Medium)
                                Text(student.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (onResetPassword != null || onDeleteStudent != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (onResetPassword != null) {
                                            OutlinedButton(onClick = { onResetPassword(student.email) }, shape = RoundedCornerShape(12.dp)) {
                                                Text("Reset")
                                            }
                                        }
                                        if (onDeleteStudent != null) {
                                            OutlinedButton(onClick = { onDeleteStudent(student.id) }, shape = RoundedCornerShape(12.dp)) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                            StatusPill(label = student.rollNo.ifBlank { "No Roll" }, color = Warm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLaunchDialog(
    className: String,
    subject: String,
    onDismiss: () -> Unit,
    onLaunch: (method: String, durationMinutes: Int) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("qr") }
    var durationText by remember { mutableStateOf("5") }
    val duration = durationText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Launch Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$className • $subject")
                listOf(
                    "qr" to "QR Code",
                    "fingerprint" to "Fingerprint",
                    "face" to "Face Scan",
                    "all" to "All Methods"
                ).forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMethod == value,
                            onClick = { selectedMethod = value }
                        )
                        Text(label)
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit) },
                    label = { Text("Duration in minutes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunch(selectedMethod, duration ?: 0) },
                enabled = duration != null && duration in 1..120
            ) {
                Text("Launch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StudentAttendanceDialog(
    entries: List<StudentAttendanceEntry>,
    overallSummary: AttendanceSummary,
    onDismiss: () -> Unit
) {
    val rows = remember(entries) {
        entries
            .groupBy { "${it.className}:::${it.subject}" }
            .toList()
            .sortedByDescending { (_, items) -> items.maxOfOrNull { it.date }.orEmpty() }
            .flatMap { (groupKey, classEntries) ->
                val parts = groupKey.split(":::")
                val className = parts.getOrElse(0) { "Class" }
                val subject = parts.getOrElse(1) { "" }
                val presentCount = classEntries.count { it.status.equals("present", ignoreCase = true) }
                val summary = AttendanceSummary(
                    total = classEntries.size,
                    present = presentCount,
                    percent = if (classEntries.isEmpty()) 0 else ((presentCount * 100f) / classEntries.size).toInt()
                )
                buildList {
                    add(
                        StudentAttendanceRow.Section(
                            StudentAttendanceSection(
                                key = groupKey,
                                className = className,
                                subject = subject,
                                summary = summary,
                                entries = emptyList()
                            )
                        )
                    )
                    classEntries
                        .sortedByDescending { it.date }
                        .forEach { entry ->
                            add(
                                StudentAttendanceRow.Entry(
                                    rowId = entry.id,
                                    status = entry.status,
                                    date = entry.date,
                                    method = entry.method
                                )
                            )
                        }
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All-Time Attendance") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Records", style = MaterialTheme.typography.bodySmall)
                            Text(entries.size.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("All-Time %", style = MaterialTheme.typography.bodySmall)
                            Text("${overallSummary.percent}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                AttendanceProgress(summary = overallSummary)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    if (entries.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Attendance Yet",
                                body = "Attendance records will appear here from the first marked session onward."
                            )
                        }
                    } else {
                        items(rows, key = {
                            when (it) {
                                is StudentAttendanceRow.Section -> "section-${it.section.key}"
                                is StudentAttendanceRow.Entry -> "entry-${it.rowId}"
                            }
                        }) { row ->
                            when (row) {
                                is StudentAttendanceRow.Section -> {
                                    val section = row.section
                                    val absentCount = section.summary.total - section.summary.present
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PanelRaised),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(section.className, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                            if (section.subject.isNotBlank()) {
                                                Text(
                                                    section.subject,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StatusPill(label = "${section.summary.present} Present", color = Success)
                                                StatusPill(label = "$absentCount Absent", color = Danger)
                                            }
                                            AttendanceProgress(summary = section.summary)
                                        }
                                    }
                                }
                                is StudentAttendanceRow.Entry -> {
                                    val isPresent = row.status.equals("present", ignoreCase = true)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(row.date, fontWeight = FontWeight.Medium)
                                                Text(
                                                    if (isPresent) {
                                                        "Method: ${row.method?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Unknown"}"
                                                    } else {
                                                        "No attendance recorded"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            StatusPill(
                                                label = if (isPresent) "Present" else "Absent",
                                                color = if (isPresent) Success else Danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun StatusPill(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AttendanceProgress(summary: AttendanceSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Attendance ${summary.present}/${summary.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${summary.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (summary.total == 0) 0f else summary.present / summary.total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = when {
                summary.percent >= 75 -> Success
                summary.percent >= 50 -> Warm
                else -> Danger
            },
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0
) {
    RevealCard(delayMillis = delayMillis) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = PanelRaised),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RevealCard(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "reveal-alpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "reveal-translate"
    )

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = translateY
            }
    ) {
        content()
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ClassStatusBadge(active: Boolean) {
    val borderColor = if (active) Color(0xFF31D98A) else Color(0xFFE65252)
    val text = if (active) "Class Active" else "Class Ended"
    val transition = rememberInfiniteTransition(label = "class-status")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "class-status-alpha"
    )
    Text(
        text = text,
        color = borderColor,
        modifier = Modifier
            .alpha(if (active) alpha else 1f)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun EnrollClassesDialog(
    classes: List<EnrollableClass>,
    onDismiss: () -> Unit,
    onEnroll: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Classes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (classes.isEmpty()) {
                    EmptyStateCard(
                        title = "No classes yet",
                        body = "No classes are available for enrollment right now."
                    )
                } else {
                    classes.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(item.subject, style = MaterialTheme.typography.bodySmall)
                                Text("Faculty: ${item.facultyName}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.enrolled) {
                                Text("Enrolled", color = Color(0xFF31D98A))
                            } else {
                                OutlinedButton(onClick = { onEnroll(item.id) }) {
                                    Text("Enroll")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FacultyAttendanceCalendarDialog(
    days: List<FacultyAttendanceDay>,
    onDismiss: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance Calendar") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(days, key = { it.date }) { day ->
                    val date = LocalDate.parse(day.date)
                    val color = when (day.status) {
                        "present" -> Color(0xFF31D98A)
                        "partial" -> Color(0xFFFFB547)
                        "absent" -> Color(0xFFE65252)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date.format(formatter))
                        Text(day.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, color = color)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AdminCreateClassDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var className by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Create the class first. Later, from Manage Class, assign the subject and faculty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(className) },
                enabled = className.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdminCreateAccountDialog(
    availableClasses: List<AdminClassOverview>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    var rollNo by remember { mutableStateOf("") }
    var selectedClassId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == "student", onClick = { role = "student" })
                        Text("Student")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == "faculty", onClick = { role = "faculty" })
                        Text("Faculty")
                    }
                }
                if (role == "student") {
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = { rollNo = it },
                        label = { Text("Roll No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Assign Class", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (availableClasses.isEmpty()) {
                        Text("No classes available yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableClasses, key = { it.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Medium)
                                            Text(item.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedClassId = if (selectedClassId == item.id) null else item.id
                                            }
                                        ) {
                                            Text(if (selectedClassId == item.id) "Selected" else "Select")
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "A student can only be assigned to one class.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, email, password, role, rollNo, listOfNotNull(selectedClassId)) },
                enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6 && (role != "student" || rollNo.isNotBlank())
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdminAttendanceAnalyticsDialog(
    analytics: AdminAttendanceAnalytics,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance Analytics") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (analytics.classes.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No analytics yet",
                            body = "Attendance analytics will appear once classes, sessions, and student attendance records exist."
                        )
                    }
                } else {
                    items(analytics.classes, key = { it.classId }) { item ->
                        AdminAttendanceAnalyticsCard(item = item)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AdminAttendanceAnalyticsCard(item: AdminAttendanceClassAnalytics) {
    val summary = remember(item.totalPresent, item.totalPresent + item.totalAbsent, item.percentage) {
        AttendanceSummary(
            total = item.totalPresent + item.totalAbsent,
            present = item.totalPresent,
            percent = item.percentage
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.className, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = "${item.totalSessions} Sessions", color = Warm)
                StatusPill(label = "${item.totalStudents} Students", color = Success)
            }
            AttendanceProgress(summary = summary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = "${item.totalPresent} Present", color = Success)
                StatusPill(label = "${item.totalAbsent} Absent", color = Danger)
            }
        }
    }
}

@Composable
private fun AdminManageClassDialog(
    detail: AdminClassDetail,
    facultyUsers: List<AdminUserOverview>,
    onDismiss: () -> Unit,
    onAssignStudent: (String) -> Unit,
    onUpdateSubject: (String, String) -> Unit,
    onDeleteClass: () -> Unit
) {
    val enrolledCount = remember(detail.students) { detail.students.count { it.enrolled } }
    var subjectText by remember(detail.subject) { mutableStateOf(detail.subject) }
    var selectedFacultyId by remember(detail.facultyId, facultyUsers) {
        mutableStateOf(
            detail.facultyId.takeIf { it.isNotBlank() }
                ?: facultyUsers.firstOrNull()?.id.orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelRaised),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(detail.className, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = subjectText,
                            onValueChange = { subjectText = it },
                            label = { Text("Subject") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Assign Faculty",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (facultyUsers.isEmpty()) {
                            Text(
                                "No faculty account found in this admin tag yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(facultyUsers, key = { "faculty-pick-${it.id}" }) { faculty ->
                                    val isSelected = selectedFacultyId == faculty.id
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(faculty.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    faculty.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { selectedFacultyId = faculty.id },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(if (isSelected) "Selected" else "Select")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val selectedFacultyName = facultyUsers.firstOrNull { it.id == selectedFacultyId }?.name.orEmpty()
                            StatusPill(label = selectedFacultyName.ifBlank { detail.facultyName.ifBlank { "Faculty Pending" } }, color = Warm)
                            StatusPill(label = "$enrolledCount Enrolled", color = Success)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onUpdateSubject(subjectText, selectedFacultyId) },
                                modifier = Modifier.weight(1f),
                                enabled = subjectText.isNotBlank() && selectedFacultyId.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Details")
                            }
                            OutlinedButton(
                                onClick = onDeleteClass,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Delete Class")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detail.students, key = { it.id }) { student ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(student.name, fontWeight = FontWeight.Medium)
                                    Text(student.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (student.rollNo.isNotBlank()) {
                                        Text("Roll No: ${student.rollNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (student.enrolled) {
                                    StatusPill(label = "Enrolled", color = Success)
                                } else {
                                    OutlinedButton(onClick = { onAssignStudent(student.id) }, shape = RoundedCornerShape(14.dp)) {
                                        Text("Assign")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ProfileAvatar(
    photoBase64: String?,
    onClick: () -> Unit
) {
    val imageBitmap = remember(photoBase64) { photoBase64?.decodeProfileImage() }
    TextButton(onClick = onClick) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ProfileDialog(
    profile: com.SmartAttend.app.data.UserProfile,
    biometricProfile: BiometricProfile,
    assignedBadges: List<String>,
    onDismiss: () -> Unit,
    onReRegisterFingerprint: () -> Unit,
    onReRegisterFace: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val bitmap = remember(biometricProfile.face?.photoBase64) { biometricProfile.face?.photoBase64?.decodeProfileImage() }
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = "Profile photo", modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Profile photo", modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)), contentScale = ContentScale.Crop)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text(profile.email)
                    if (profile.role == "student") {
                        Text(profile.rollNo)
                    }
                    Text(profile.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.orgTag.isNotBlank()) {
                            StatusPill(label = profile.orgTag, color = Warm)
                        }
                    }
                }
                if (assignedBadges.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Assigned Classes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            assignedBadges.sorted().forEach { className ->
                                StatusPill(label = className, color = Success)
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    if (profile.role != "admin") {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onReRegisterFace, modifier = Modifier.fillMaxWidth()) { Text("Re-register Face") }
                            OutlinedButton(onClick = onReRegisterFingerprint, modifier = Modifier.fillMaxWidth()) { Text("Re-register Fingerprint") }
                        }
                    } else {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Admin accounts do not use biometric setup in this panel.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun String.decodeProfileImage() = runCatching {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
