package com.SmartAttend.app.data

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val rollNo: String = "",
    val orgTag: String = ""
)

data class FingerprintProfile(
    val registeredAt: String? = null,
    val device: String? = null
)

data class FaceProfile(
    val registeredAt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val photoBase64: String? = null,
    val signature: String? = null
)

data class BiometricProfile(
    val fingerprint: FingerprintProfile? = null,
    val face: FaceProfile? = null
)

data class Enrollment(
    val studentId: String = "",
    val classId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val enrolledAt: String = ""
)

data class ClassRoom(
    val name: String = "",
    val subject: String = "",
    val facultyId: String = "",
    val facultyName: String = "",
    val classActive: Boolean = false,
    val orgTag: String = ""
)

data class SessionRecord(
    val classId: String = "",
    val active: Boolean = false,
    val expiresAt: String = "",
    val token: String? = null,
    val attendanceMethod: String? = null,
    val allowedMethods: List<String>? = null
)

data class ActiveSession(
    val id: String,
    val classId: String,
    val className: String,
    val subject: String,
    val methods: List<String>,
    val expiresAt: String,
    val token: String? = null
)

data class FacultyClassOverview(
    val id: String,
    val name: String,
    val subject: String,
    val classActive: Boolean,
    val activeSession: ActiveSession? = null
)

data class StudentClassOverview(
    val id: String,
    val name: String,
    val subject: String,
    val facultyName: String,
    val classActive: Boolean,
    val activeSession: ActiveSession? = null,
    val activeSessionMarked: Boolean = false
)

data class EnrollableClass(
    val id: String,
    val name: String,
    val subject: String,
    val facultyName: String,
    val enrolled: Boolean
)

data class FacultyAttendanceDay(
    val date: String,
    val status: String
)

data class StudentAttendanceEntry(
    val id: String,
    val className: String,
    val subject: String,
    val date: String,
    val method: String?,
    val status: String
)

data class AdminUserOverview(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val rollNo: String = "",
    val orgTag: String = ""
)

data class AdminClassOverview(
    val id: String,
    val name: String,
    val subject: String,
    val facultyId: String,
    val facultyName: String,
    val studentCount: Int,
    val classActive: Boolean,
    val orgTag: String = ""
)

data class AdminLiveSessionOverview(
    val id: String,
    val className: String,
    val subject: String,
    val facultyName: String,
    val methods: List<String>,
    val expiresAt: String
)

data class AdminDashboardData(
    val users: List<AdminUserOverview> = emptyList(),
    val classes: List<AdminClassOverview> = emptyList(),
    val liveSessions: List<AdminLiveSessionOverview> = emptyList(),
    val todayAttendanceCount: Int = 0
)

data class AdminAttendanceAnalytics(
    val classes: List<AdminAttendanceClassAnalytics> = emptyList()
)

data class AdminAttendanceClassAnalytics(
    val classId: String,
    val className: String,
    val subject: String,
    val totalSessions: Int,
    val totalStudents: Int,
    val totalPresent: Int,
    val totalAbsent: Int,
    val percentage: Int
)

data class AdminStudentOption(
    val id: String,
    val name: String,
    val email: String,
    val rollNo: String,
    val enrolled: Boolean
)

data class AdminClassDetail(
    val classId: String,
    val className: String,
    val subject: String,
    val facultyId: String,
    val facultyName: String,
    val students: List<AdminStudentOption> = emptyList()
)

data class StudentRosterItem(
    val id: String,
    val name: String,
    val email: String,
    val rollNo: String
)

data class ClassStudentGroup(
    val classId: String,
    val className: String,
    val subject: String,
    val students: List<StudentRosterItem> = emptyList()
)

data class AdminDeleteResult(
    val authDeleted: Boolean,
    val message: String
)
