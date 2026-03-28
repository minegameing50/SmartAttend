package com.SmartAttend.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SmartAttend.app.data.ActiveSession
import com.SmartAttend.app.data.AdminAttendanceAnalytics
import com.SmartAttend.app.data.AdminClassDetail
import com.SmartAttend.app.data.AdminDashboardData
import com.SmartAttend.app.data.BiometricProfile
import com.SmartAttend.app.data.ClassStudentGroup
import com.SmartAttend.app.data.EnrollableClass
import com.SmartAttend.app.data.FacultyAttendanceDay
import com.SmartAttend.app.data.FacultyClassOverview
import com.SmartAttend.app.data.FirebaseRepository
import com.SmartAttend.app.data.StudentClassOverview
import com.SmartAttend.app.data.StudentAttendanceEntry
import com.SmartAttend.app.data.UserProfile
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val currentRole: String? = null,
    val profile: UserProfile? = null,
    val biometricProfile: BiometricProfile = BiometricProfile(),
    val sessions: List<ActiveSession> = emptyList(),
    val studentClasses: List<StudentClassOverview> = emptyList(),
    val studentAttendanceHistory: List<StudentAttendanceEntry> = emptyList(),
    val enrollableClasses: List<EnrollableClass> = emptyList(),
    val facultyClasses: List<FacultyClassOverview> = emptyList(),
    val facultyAttendanceMarkedToday: Boolean = false,
    val facultyAttendanceMethodsToday: Set<String> = emptySet(),
    val facultyAttendanceCalendar: List<FacultyAttendanceDay> = emptyList(),
    val facultyStudentGroups: List<ClassStudentGroup> = emptyList(),
    val adminDashboard: AdminDashboardData = AdminDashboardData(),
    val adminAttendanceAnalytics: AdminAttendanceAnalytics = AdminAttendanceAnalytics(),
    val adminStudentGroups: List<ClassStudentGroup> = emptyList(),
    val adminSelectedClassDetail: AdminClassDetail? = null,
    val loginError: String? = null,
    val statusMessage: String? = null
)

class AppViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user == null) {
                _uiState.value = AppUiState(loading = false)
                return@launch
            }
            loadProfile()
        }
    }

    fun login(email: String, password: String, expectedRole: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, loginError = null, statusMessage = null)
            runCatching {
                val requestedEmail = email.trim()
                repository.signIn(requestedEmail, password)
                val profile = repository.getCurrentProfile()
                    ?: error("Permission denied")
                if (profile.role.lowercase() != expectedRole.lowercase()) {
                    repository.signOut()
                    error("Permission denied")
                }
                loadProfile()
            }.onFailure { error ->
                _uiState.value = AppUiState(
                    loading = false,
                    loginError = "Permission denied"
                )
            }
        }
    }

    fun markFingerprintAttendance(sessionId: String) {
        handleStudentOperation(successMessage = "Fingerprint attendance recorded successfully.") { profile ->
            repository.markFingerprintAttendance(profile, sessionId)
        }
    }

    fun markQrAttendance(rawValue: String) {
        handleStudentOperation(successMessage = "QR attendance recorded successfully.") { profile ->
            repository.markQrAttendance(profile, rawValue)
        }
    }

    fun registerFace(width: Int, height: Int, photoBase64: String, signature: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                val biometricProfile = repository.registerFace(profile, width, height, photoBase64, signature)
                val studentClasses = repository.getStudentClasses(profile)
                biometricProfile to studentClasses
            }.onSuccess { (biometricProfile, studentClasses) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    biometricProfile = biometricProfile,
                    studentClasses = studentClasses,
                    statusMessage = "Face registration saved."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Face registration failed."
                )
            }
        }
    }

    fun registerFingerprint(device: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                val biometricProfile = repository.registerFingerprint(profile, device)
                val studentClasses = repository.getStudentClasses(profile)
                biometricProfile to studentClasses
            }.onSuccess { (biometricProfile, studentClasses) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    biometricProfile = biometricProfile,
                    studentClasses = studentClasses,
                    statusMessage = "Fingerprint setup saved."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Fingerprint setup failed."
                )
            }
        }
    }

    fun markFaceAttendance(sessionId: String, width: Int, height: Int, signature: String) {
        handleStudentOperation(successMessage = "Face attendance recorded successfully.") { profile ->
            repository.markFaceAttendance(profile, sessionId, width, height, signature)
        }
    }

    fun logout() {
        repository.signOut()
        _uiState.value = AppUiState(loading = false)
    }

    fun launchFacultySession(classId: String, method: String, durationMinutes: Int) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.launchFacultySession(profile, classId, method, durationMinutes)
                repository.getFacultyClasses(profile)
            }.onSuccess { classes ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    facultyClasses = classes,
                    statusMessage = "Faculty session launched."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Session launch failed."
                )
            }
        }
    }

    fun endFacultyClass(classId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.endFacultyClass(profile, classId)
                repository.getFacultyClasses(profile)
            }.onSuccess { classes ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    facultyClasses = classes,
                    statusMessage = "Class ended."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to end class."
                )
            }
        }
    }

    fun markFacultyAttendance(
        method: String,
        width: Int? = null,
        height: Int? = null,
        photoBase64: String? = null,
        signature: String? = null
    ) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                val extra = if (method == "face" && width != null && height != null) {
                    mapOf(
                        "faceCapture" to mapOf(
                            "width" to width,
                            "height" to height,
                            "capturedAt" to java.time.Instant.now().toString()
                        )
                    )
                } else {
                    emptyMap()
                }
                repository.markFacultyAttendance(
                    profile = profile,
                    method = method,
                    extra = extra,
                    device = if (method == "fingerprint") (android.os.Build.MODEL ?: "Android device") else null,
                    faceWidth = width,
                    faceHeight = height,
                    facePhotoBase64 = photoBase64,
                    faceSignature = signature
                )
                val methodsToday = repository.getFacultyAttendanceMethodsToday(profile)
                Pair(
                    Quadruple(
                        repository.getBiometricProfile(profile),
                        repository.getFacultyClasses(profile),
                        methodsToday,
                        repository.getFacultyAttendanceCalendar(profile)
                    ),
                    repository.getFacultyStudentGroups(profile)
                )
            }.onSuccess { (payload, facultyStudentGroups) ->
                val (biometricProfile, classes, methodsToday, calendar) = payload
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    biometricProfile = biometricProfile,
                    facultyClasses = classes,
                    facultyAttendanceMarkedToday = "fingerprint" in methodsToday && "face" in methodsToday,
                    facultyAttendanceMethodsToday = methodsToday,
                    facultyAttendanceCalendar = calendar,
                    facultyStudentGroups = facultyStudentGroups,
                    statusMessage = "Faculty attendance marked."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to mark faculty attendance."
                )
            }
        }
    }

    fun loadEnrollableClasses() {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching { repository.getEnrollableClasses(profile) }
                .onSuccess { classes ->
                    _uiState.value = _uiState.value.copy(enrollableClasses = classes)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(statusMessage = error.message ?: "Unable to load classes.")
                }
        }
    }

    fun loadFacultyAttendanceCalendar() {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching { repository.getFacultyAttendanceCalendar(profile) }
                .onSuccess { calendar ->
                    _uiState.value = _uiState.value.copy(facultyAttendanceCalendar = calendar)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(statusMessage = error.message ?: "Unable to load attendance calendar.")
                }
        }
    }

    fun enrollInClass(classId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.enrollInClass(profile, classId)
                refreshCurrentData()
                repository.getEnrollableClasses(profile)
            }.onSuccess { classes ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    enrollableClasses = classes,
                    statusMessage = "Class enrolled successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to enroll in class."
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun createAdminClass(name: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.createClass(profile, name)
                Pair(
                    repository.getAdminDashboard(profile),
                    repository.getAdminAttendanceAnalytics(profile)
                )
            }.onSuccess { (adminDashboard, adminAttendanceAnalytics) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = adminDashboard,
                    adminAttendanceAnalytics = adminAttendanceAnalytics,
                    statusMessage = "Class created successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to create class."
                )
            }
        }
    }

    fun updateAdminClassDetails(classId: String, subject: String, facultyId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.updateAdminClassDetails(profile, classId, subject, facultyId)
                Pair(
                    repository.getAdminDashboard(profile),
                    repository.getAdminClassDetail(profile, classId)
                )
            }.onSuccess { (adminDashboard, adminSelectedClassDetail) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = adminDashboard,
                    adminSelectedClassDetail = adminSelectedClassDetail,
                    statusMessage = "Class details updated successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to update class details."
                )
            }
        }
    }

    fun deleteAdminClass(classId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.deleteAdminClass(profile, classId)
                Triple(
                    repository.getAdminDashboard(profile),
                    repository.getAdminAttendanceAnalytics(profile),
                    repository.getAdminStudentGroups(profile)
                )
            }.onSuccess { (adminDashboard, adminAttendanceAnalytics, adminStudentGroups) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = adminDashboard,
                    adminAttendanceAnalytics = adminAttendanceAnalytics,
                    adminStudentGroups = adminStudentGroups,
                    adminSelectedClassDetail = null,
                    statusMessage = "Class deleted successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to delete class."
                )
            }
        }
    }

    fun loadAdminAttendanceAnalytics() {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching { repository.getAdminAttendanceAnalytics(profile) }
                .onSuccess { analytics ->
                    _uiState.value = _uiState.value.copy(adminAttendanceAnalytics = analytics)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = error.message ?: "Unable to load attendance analytics."
                    )
                }
        }
    }

    fun loadAdminClassDetail(classId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching { repository.getAdminClassDetail(profile, classId) }
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(adminSelectedClassDetail = detail)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = error.message ?: "Unable to load class detail."
                    )
                }
        }
    }

    fun clearAdminClassDetail() {
        _uiState.value = _uiState.value.copy(adminSelectedClassDetail = null)
    }

    fun adminAssignStudentToClass(classId: String, studentId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.adminAssignStudentToClass(profile, classId, studentId)
                Triple(
                    repository.getAdminDashboard(profile),
                    repository.getAdminAttendanceAnalytics(profile),
                    repository.getAdminClassDetail(profile, classId)
                ) to repository.getAdminStudentGroups(profile)
            }.onSuccess { (payload, adminStudentGroups) ->
                val (adminDashboard, adminAttendanceAnalytics, adminSelectedClassDetail) = payload
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = adminDashboard,
                    adminAttendanceAnalytics = adminAttendanceAnalytics,
                    adminStudentGroups = adminStudentGroups,
                    adminSelectedClassDetail = adminSelectedClassDetail,
                    statusMessage = "Student assigned successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to assign student."
                )
            }
        }
    }

    fun createAdminAccount(
        context: Context,
        name: String,
        email: String,
        password: String,
        role: String,
        rollNo: String,
        classIds: List<String>
    ) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.createAdminManagedAccount(context, profile, name, email, password, role, rollNo, classIds)
                Pair(
                    repository.getAdminDashboard(profile),
                    repository.getAdminStudentGroups(profile)
                )
            }.onSuccess { (adminDashboard, adminStudentGroups) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = adminDashboard,
                    adminStudentGroups = adminStudentGroups,
                    statusMessage = "Account created successfully."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to create account."
                )
            }
        }
    }

    fun adminSendPasswordReset(email: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                repository.sendAdminPasswordReset(profile, email)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = "Password reset email sent."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to send password reset email."
                )
            }
        }
    }

    fun deleteAdminManagedAccount(userId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                val deleteResult = repository.deleteAdminManagedAccount(profile, userId)
                DeleteAdminAccountRefresh(
                    adminDashboard = repository.getAdminDashboard(profile),
                    adminStudentGroups = repository.getAdminStudentGroups(profile),
                    adminAttendanceAnalytics = repository.getAdminAttendanceAnalytics(profile),
                    statusMessage = deleteResult.message
                )
            }.onSuccess { refresh ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    adminDashboard = refresh.adminDashboard,
                    adminStudentGroups = refresh.adminStudentGroups,
                    adminAttendanceAnalytics = refresh.adminAttendanceAnalytics,
                    statusMessage = refresh.statusMessage
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Unable to delete account."
                )
            }
        }
    }

    private data class DeleteAdminAccountRefresh(
        val adminDashboard: AdminDashboardData,
        val adminStudentGroups: List<ClassStudentGroup>,
        val adminAttendanceAnalytics: AdminAttendanceAnalytics,
        val statusMessage: String
    )

    private suspend fun loadProfile() {
        val profile = repository.getCurrentProfile() ?: error("User profile was not found in Firebase.")
        val biometricProfile = repository.getBiometricProfile(profile)
        val sessions = if (profile.role == "student") {
            repository.getActiveStudentSessions(profile)
        } else {
            emptyList()
        }
        val facultyClasses = if (profile.role == "faculty") {
            repository.getFacultyClasses(profile)
        } else {
            emptyList()
        }
        val studentClasses = if (profile.role == "student") {
            repository.getStudentClasses(profile)
        } else {
            emptyList()
        }
        val studentAttendanceHistory = if (profile.role == "student") {
            repository.getStudentAttendanceHistory(profile)
        } else {
            emptyList()
        }
        val facultyAttendanceMarkedToday = if (profile.role == "faculty") {
            repository.hasFacultyAttendanceToday(profile)
        } else {
            false
        }
        val facultyAttendanceMethodsToday = if (profile.role == "faculty") {
            repository.getFacultyAttendanceMethodsToday(profile)
        } else {
            emptySet()
        }
        val facultyAttendanceCalendar = if (profile.role == "faculty") {
            repository.getFacultyAttendanceCalendar(profile)
        } else {
            emptyList()
        }
        val facultyStudentGroups = if (profile.role == "faculty") {
            repository.getFacultyStudentGroups(profile)
        } else {
            emptyList()
        }
        val adminDashboard = if (profile.role == "admin") {
            repository.getAdminDashboard(profile)
        } else {
            AdminDashboardData()
        }
        val adminAttendanceAnalytics = if (profile.role == "admin") {
            repository.getAdminAttendanceAnalytics(profile)
        } else {
            AdminAttendanceAnalytics()
        }
        val adminStudentGroups = if (profile.role == "admin") {
            repository.getAdminStudentGroups(profile)
        } else {
            emptyList()
        }
        _uiState.value = AppUiState(
            loading = false,
            currentRole = profile.role,
            profile = profile,
            biometricProfile = biometricProfile,
            sessions = sessions,
            studentClasses = studentClasses,
            studentAttendanceHistory = studentAttendanceHistory,
            facultyClasses = facultyClasses,
            facultyAttendanceMarkedToday = facultyAttendanceMarkedToday,
            facultyAttendanceMethodsToday = facultyAttendanceMethodsToday,
            facultyAttendanceCalendar = facultyAttendanceCalendar,
            facultyStudentGroups = facultyStudentGroups,
            adminDashboard = adminDashboard,
            adminAttendanceAnalytics = adminAttendanceAnalytics,
            adminStudentGroups = adminStudentGroups
        )
    }

    fun refreshCurrentData(lightweight: Boolean = false) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching {
                val biometricProfile = if (lightweight) _uiState.value.biometricProfile else repository.getBiometricProfile(profile)
                val studentClasses = if (profile.role == "student") repository.getStudentClasses(profile) else emptyList()
                val studentAttendanceHistory = if (profile.role == "student" && !lightweight) repository.getStudentAttendanceHistory(profile) else _uiState.value.studentAttendanceHistory
                val facultyClasses = if (profile.role == "faculty") repository.getFacultyClasses(profile) else emptyList()
                val facultyAttendanceMarkedToday = if (profile.role == "faculty") repository.hasFacultyAttendanceToday(profile) else false
                val facultyAttendanceMethodsToday = if (profile.role == "faculty") repository.getFacultyAttendanceMethodsToday(profile) else emptySet()
                val facultyAttendanceCalendar = if (profile.role == "faculty" && !lightweight) repository.getFacultyAttendanceCalendar(profile) else _uiState.value.facultyAttendanceCalendar
                val facultyStudentGroups = if (profile.role == "faculty" && !lightweight) repository.getFacultyStudentGroups(profile) else _uiState.value.facultyStudentGroups
                val adminDashboard = if (profile.role == "admin" && !lightweight) repository.getAdminDashboard(profile) else _uiState.value.adminDashboard
                val adminAttendanceAnalytics = if (profile.role == "admin" && !lightweight) repository.getAdminAttendanceAnalytics(profile) else _uiState.value.adminAttendanceAnalytics
                val adminStudentGroups = if (profile.role == "admin" && !lightweight) repository.getAdminStudentGroups(profile) else _uiState.value.adminStudentGroups
                RefreshPayload(biometricProfile, studentClasses, studentAttendanceHistory, facultyClasses, facultyAttendanceMarkedToday, facultyAttendanceMethodsToday, facultyAttendanceCalendar, facultyStudentGroups, adminDashboard, adminAttendanceAnalytics, adminStudentGroups)
            }.onSuccess { payload ->
                _uiState.value = _uiState.value.copy(
                    biometricProfile = payload.biometricProfile,
                    studentClasses = payload.studentClasses,
                    studentAttendanceHistory = payload.studentAttendanceHistory,
                    facultyClasses = payload.facultyClasses,
                    facultyAttendanceMarkedToday = payload.facultyAttendanceMarkedToday,
                    facultyAttendanceMethodsToday = payload.facultyAttendanceMethodsToday,
                    facultyAttendanceCalendar = payload.facultyAttendanceCalendar,
                    facultyStudentGroups = payload.facultyStudentGroups,
                    adminDashboard = payload.adminDashboard,
                    adminAttendanceAnalytics = payload.adminAttendanceAnalytics,
                    adminStudentGroups = payload.adminStudentGroups
                )
            }
        }
    }

    private fun handleStudentOperation(
        successMessage: String,
        action: suspend (UserProfile) -> Unit
    ) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, statusMessage = null)
            runCatching {
                action(profile)
                val biometricProfile = repository.getBiometricProfile(profile)
                val sessions = repository.getActiveStudentSessions(profile)
                val studentClasses = repository.getStudentClasses(profile)
                Triple(biometricProfile, sessions, studentClasses)
            }.onSuccess { (biometricProfile, sessions, studentClasses) ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    biometricProfile = biometricProfile,
                    sessions = sessions,
                    studentClasses = studentClasses,
                    statusMessage = successMessage
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = error.message ?: "Attendance failed."
                )
            }
        }
    }
}

private data class RefreshPayload(
    val biometricProfile: BiometricProfile,
    val studentClasses: List<StudentClassOverview>,
    val studentAttendanceHistory: List<StudentAttendanceEntry>,
    val facultyClasses: List<FacultyClassOverview>,
    val facultyAttendanceMarkedToday: Boolean,
    val facultyAttendanceMethodsToday: Set<String>,
    val facultyAttendanceCalendar: List<FacultyAttendanceDay>,
    val facultyStudentGroups: List<ClassStudentGroup>,
    val adminDashboard: AdminDashboardData,
    val adminAttendanceAnalytics: AdminAttendanceAnalytics,
    val adminStudentGroups: List<ClassStudentGroup>
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
