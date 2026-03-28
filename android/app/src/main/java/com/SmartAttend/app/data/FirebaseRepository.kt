package com.SmartAttend.app.data

import android.content.Context
import android.util.Log
import com.SmartAttend.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.IOException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class FirebaseRepository {
    companion object {
        private const val TAG = "SmartAttendDelete"
    }

    private val auth = Firebase.auth
    private val database = Firebase.database.reference

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun getCurrentProfile(): UserProfile? {
        val currentUser = auth.currentUser ?: return null
        val profileSnapshot = database.child("users").child(currentUser.uid).get().await()
        val profile = profileSnapshot.getValue(UserProfile::class.java) ?: return null
        return profile.copy(uid = currentUser.uid)
    }

    suspend fun getBiometricProfile(profile: UserProfile): BiometricProfile {
        if (profile.uid.isBlank()) return BiometricProfile()
        val snapshot = database.child("users").child(profile.uid).child("biometrics").get().await()
        return snapshot.getValue(BiometricProfile::class.java) ?: BiometricProfile()
    }

    suspend fun registerFace(profile: UserProfile, width: Int, height: Int, photoBase64: String, signature: String): BiometricProfile {
        if (profile.uid.isBlank()) error("User profile is missing.")
        val existing = getBiometricProfile(profile)
        val updated = mapOf(
            "face" to mapOf(
                "registeredAt" to Instant.now().toString(),
                "width" to width,
                "height" to height,
                "photoBase64" to photoBase64,
                "signature" to signature
            ),
            "fingerprint" to existing.fingerprint?.let {
                mapOf(
                    "registeredAt" to it.registeredAt,
                    "device" to it.device
                )
            }
        ).filterValues { it != null }
        database.child("users").child(profile.uid).child("biometrics").setValue(updated).await()
        return getBiometricProfile(profile)
    }

    suspend fun registerFingerprint(profile: UserProfile, device: String): BiometricProfile {
        if (profile.uid.isBlank()) error("User profile is missing.")
        val existing = getBiometricProfile(profile)
        val updated = mapOf(
            "fingerprint" to mapOf(
                "registeredAt" to Instant.now().toString(),
                "device" to device
            ),
            "face" to existing.face?.let {
                mapOf(
                    "registeredAt" to it.registeredAt,
                    "width" to it.width,
                    "height" to it.height,
                    "photoBase64" to it.photoBase64,
                    "signature" to it.signature
                )
            }
        ).filterValues { it != null }
        database.child("users").child(profile.uid).child("biometrics").setValue(updated).await()
        return getBiometricProfile(profile)
    }

    suspend fun getActiveStudentSessions(profile: UserProfile): List<ActiveSession> {
        val orgTag = profile.orgTag.trim()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val classIds = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            enrollment.classId.takeIf { enrollment.studentId == profile.uid }
        }.toSet()

        if (classIds.isEmpty()) return emptyList()

        val classesSnapshot = database.child("classes").get().await()
        val classes = classesSnapshot.children.associate { node ->
            node.key.orEmpty() to (node.getValue(ClassRoom::class.java) ?: ClassRoom())
        }

        val now = Instant.now()
        val sessionsSnapshot = database.child("sessions").get().await()
        return sessionsSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            val sessionId = node.key.orEmpty()
            if (session.classId !in classIds || !session.active) return@mapNotNull null
            if (session.expiresAt.isBlank()) return@mapNotNull null

            val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull() ?: return@mapNotNull null
            if (expiresAt.isBefore(now)) return@mapNotNull null

            val classRoom = classes[session.classId] ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            ActiveSession(
                id = sessionId,
                classId = session.classId,
                className = classRoom.name,
                subject = classRoom.subject,
                methods = getMethods(session),
                expiresAt = session.expiresAt,
                token = session.token
            )
        }.sortedBy { it.expiresAt }
    }

    suspend fun getFacultyClasses(profile: UserProfile): List<FacultyClassOverview> {
        val orgTag = profile.orgTag.trim()
        val classesSnapshot = database.child("classes").get().await()
        val sessionSnapshot = database.child("sessions").get().await()
        val now = Instant.now()

        val sessionsByClass = sessionSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            val sessionId = node.key.orEmpty()
            val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull()
            if (!session.active || expiresAt == null || expiresAt.isBefore(now)) return@mapNotNull null
            session.classId to (sessionId to session)
        }.groupBy({ it.first }, { it.second })

        return classesSnapshot.children.mapNotNull { node ->
            val classId = node.key.orEmpty()
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.facultyId != profile.uid) return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null

            val activeSession = sessionsByClass[classId]
                ?.maxByOrNull { (_, session) -> session.expiresAt }
                ?.let { (sessionId, session) ->
                    ActiveSession(
                        id = sessionId,
                        classId = classId,
                        className = classRoom.name,
                        subject = classRoom.subject,
                        methods = getMethods(session),
                        expiresAt = session.expiresAt,
                        token = session.token
                    )
                }

            FacultyClassOverview(
                id = classId,
                name = classRoom.name,
                subject = classRoom.subject,
                classActive = classRoom.classActive,
                activeSession = activeSession
            )
        }.sortedBy { it.name }
    }

    suspend fun getStudentClasses(profile: UserProfile): List<StudentClassOverview> {
        val orgTag = profile.orgTag.trim()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val classesSnapshot = database.child("classes").get().await()
        val sessionsSnapshot = database.child("sessions").get().await()
        val attendanceSnapshot = database.child("attendance").get().await()
        val now = Instant.now()

        val myClassIds = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            enrollment.classId.takeIf { enrollment.studentId == profile.uid }
        }.toSet()

        val attendanceBySession = attendanceSnapshot.children.mapNotNull { node ->
            val sessionId = node.child("sessionId").getValue(String::class.java) ?: return@mapNotNull null
            val studentId = node.child("studentId").getValue(String::class.java) ?: return@mapNotNull null
            sessionId to studentId
        }.toSet()

        val liveSessionsByClass = sessionsSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            val sessionId = node.key.orEmpty()
            val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull()
            if (!session.active || expiresAt == null || expiresAt.isBefore(now)) return@mapNotNull null
            session.classId to (sessionId to session)
        }.groupBy({ it.first }, { it.second })

        return classesSnapshot.children.mapNotNull { node ->
            val classId = node.key.orEmpty()
            if (classId !in myClassIds) return@mapNotNull null
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            val activeSession = liveSessionsByClass[classId]
                ?.maxByOrNull { (_, session) -> session.expiresAt }
                ?.let { (sessionId, session) ->
                    ActiveSession(
                        id = sessionId,
                        classId = classId,
                        className = classRoom.name,
                        subject = classRoom.subject,
                        methods = getMethods(session),
                        expiresAt = session.expiresAt,
                        token = session.token
                    )
                }

            StudentClassOverview(
                id = classId,
                name = classRoom.name,
                subject = classRoom.subject,
                facultyName = classRoom.facultyName,
                classActive = classRoom.classActive,
                activeSession = activeSession,
                activeSessionMarked = activeSession?.let { it.id to profile.uid } in attendanceBySession
            )
        }.sortedBy { it.name }
    }

    suspend fun getEnrollableClasses(profile: UserProfile): List<EnrollableClass> {
        val orgTag = profile.orgTag.trim()
        val classesSnapshot = database.child("classes").get().await()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val myClassIds = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            enrollment.classId.takeIf { enrollment.studentId == profile.uid }
        }.toSet()

        return classesSnapshot.children.mapNotNull { node ->
            val classId = node.key.orEmpty()
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            EnrollableClass(
                id = classId,
                name = classRoom.name,
                subject = classRoom.subject,
                facultyName = classRoom.facultyName,
                enrolled = classId in myClassIds
            )
        }.sortedBy { it.name }
    }

    suspend fun getStudentAttendanceHistory(profile: UserProfile): List<StudentAttendanceEntry> {
        val orgTag = profile.orgTag.trim()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val sessionsSnapshot = database.child("sessions").get().await()
        val attendanceSnapshot = database.child("attendance").get().await()
        val classesSnapshot = database.child("classes").get().await()
        val classes = classesSnapshot.children.associate { node ->
            node.key.orEmpty() to (node.getValue(ClassRoom::class.java) ?: ClassRoom())
        }
        val myEnrolledClassIds = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            if (enrollment.studentId != profile.uid) return@mapNotNull null
            enrollment.classId
        }.toSet()
        
        if (myEnrolledClassIds.isEmpty()) return emptyList()

        val attendanceBySession = attendanceSnapshot.children.mapNotNull { node ->
            val sessionId = node.child("sessionId").getValue(String::class.java) ?: return@mapNotNull null
            val studentId = node.child("studentId").getValue(String::class.java) ?: return@mapNotNull null
            if (studentId != profile.uid) return@mapNotNull null
            sessionId to node
        }.toMap()

        return sessionsSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            val classId = session.classId
            if (classId !in myEnrolledClassIds) return@mapNotNull null
            val sessionId = node.key.orEmpty()
            val classRoom = classes[classId] ?: ClassRoom()
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            val sessionDate = node.child("date").getValue(String::class.java)
            val createdAt = node.child("createdAt").getValue(String::class.java)
            val normalizedDate = when {
                !sessionDate.isNullOrBlank() -> runCatching {
                    LocalDate.parse(sessionDate, DateTimeFormatter.ofPattern("d/M/yyyy")).toString()
                }.getOrNull()
                !createdAt.isNullOrBlank() -> runCatching {
                    Instant.parse(createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                }.getOrNull()
                else -> "Unknown"
            } ?: "Invalid Date"

            val attendanceNode = attendanceBySession[sessionId]
            val status = if (attendanceNode != null) "present" else "absent"
            val method = attendanceNode?.child("method")?.getValue(String::class.java)
            
            StudentAttendanceEntry(
                id = sessionId,
                className = classRoom.name,
                subject = classRoom.subject,
                date = normalizedDate,
                method = method,
                status = status
            )
        }.sortedByDescending { it.date }
    }

    suspend fun enrollInClass(profile: UserProfile, classId: String) {
        val enrollmentSnapshot = database.child("enrollments").get().await()
        val alreadyEnrolled = enrollmentSnapshot.children.any { node ->
            val enrollment = node.getValue(Enrollment::class.java)
            enrollment?.studentId == profile.uid && enrollment.classId == classId
        }
        if (alreadyEnrolled) return

        val payload = mapOf(
            "studentId" to profile.uid,
            "classId" to classId,
            "studentName" to profile.name,
            "rollNo" to profile.rollNo,
            "enrolledAt" to Instant.now().toString()
        )
        database.child("enrollments").push().setValue(payload).await()
    }

    suspend fun launchFacultySession(profile: UserProfile, classId: String, method: String, durationMinutes: Int) {
        require(profile.role == "faculty") { "Faculty access required." }
        val sessionId = database.child("sessions").push().key ?: error("Failed to generate session ID.")
        val expiresAt = Instant.now().plus(durationMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES).toString()
        
        val payload = mapOf(
            "classId" to classId,
            "active" to true,
            "expiresAt" to expiresAt,
            "attendanceMethod" to method,
            "token" to UUID.randomUUID().toString(),
            "createdAt" to Instant.now().toString()
        )
        database.child("sessions").child(sessionId).setValue(payload).await()
        database.child("classes").child(classId).child("classActive").setValue(true).await()
    }

    suspend fun endFacultyClass(profile: UserProfile, classId: String) {
        require(profile.role == "faculty") { "Faculty access required." }
        val sessionSnapshot = database.child("sessions").get().await()
        val activeSessions = sessionSnapshot.children.filter { node ->
            val session = node.getValue(SessionRecord::class.java)
            session?.classId == classId && session.active
        }
        
        activeSessions.forEach { node ->
            database.child("sessions").child(node.key!!).child("active").setValue(false).await()
        }
        database.child("classes").child(classId).child("classActive").setValue(false).await()
    }

    suspend fun hasFacultyAttendanceToday(profile: UserProfile): Boolean {
        return getFacultyAttendanceMethodsToday(profile).isNotEmpty()
    }

    suspend fun getFacultyAttendanceMethodsToday(profile: UserProfile): Set<String> {
        val today = LocalDate.now().toString()
        val attendanceSnapshot = database.child("facultyAttendance").get().await()
        return attendanceSnapshot.children.mapNotNull { node ->
            val facultyId = node.child("facultyId").getValue(String::class.java)
            val date = node.child("date").getValue(String::class.java)
            val method = node.child("method").getValue(String::class.java)
            if (facultyId == profile.uid && date == today) method else null
        }.toSet()
    }

    suspend fun getFacultyAttendanceCalendar(profile: UserProfile): List<FacultyAttendanceDay> {
        val attendanceSnapshot = database.child("facultyAttendance").get().await()
        val days = attendanceSnapshot.children.mapNotNull { node ->
            val facultyId = node.child("facultyId").getValue(String::class.java)
            val date = node.child("date").getValue(String::class.java)
            if (facultyId == profile.uid && date != null) date else null
        }.distinct()
        
        return days.map { date ->
            FacultyAttendanceDay(date = date, status = "Present")
        }.sortedByDescending { it.date }
    }

    suspend fun markFacultyAttendance(
        profile: UserProfile,
        method: String,
        extra: Map<String, Any?>,
        device: String? = null,
        faceWidth: Int? = null,
        faceHeight: Int? = null,
        facePhotoBase64: String? = null,
        faceSignature: String? = null
    ) {
        val today = LocalDate.now().toString()
        val payload = mutableMapOf<String, Any?>(
            "facultyId" to profile.uid,
            "facultyName" to profile.name,
            "date" to today,
            "markedAt" to Instant.now().toString(),
            "method" to method
        )
        payload.putAll(extra)
        database.child("facultyAttendance").push().setValue(payload).await()
    }

    suspend fun getAdminDashboard(profile: UserProfile): AdminDashboardData {
        require(profile.role == "admin") { "Admin access required." }
        val orgTag = profile.orgTag.trim()
        val usersSnapshot = database.child("users").get().await()
        val classesSnapshot = database.child("classes").get().await()
        val sessionsSnapshot = database.child("sessions").get().await()
        val attendanceSnapshot = database.child("attendance").get().await()
        val now = Instant.now()
        val today = LocalDate.now().toString()

        val users = usersSnapshot.children.mapNotNull { node ->
            val user = node.getValue(UserProfile::class.java) ?: return@mapNotNull null
            if (user.orgTag.trim() != orgTag) return@mapNotNull null
            AdminUserOverview(
                id = node.key.orEmpty(),
                name = user.name,
                email = user.email,
                role = user.role,
                rollNo = user.rollNo,
                orgTag = user.orgTag
            )
        }.sortedWith(compareBy<AdminUserOverview> { roleSortOrder(it.role) }.thenBy { it.name })

        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val enrollmentCountByClass = enrollmentsSnapshot.children.mapNotNull { node ->
            node.child("classId").getValue(String::class.java)
        }.groupingBy { it }.eachCount()

        val classes = classesSnapshot.children.mapNotNull { node ->
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            val classId = node.key.orEmpty()
            AdminClassOverview(
                id = classId,
                name = classRoom.name,
                subject = classRoom.subject,
                facultyId = classRoom.facultyId,
                facultyName = classRoom.facultyName,
                studentCount = enrollmentCountByClass[classId] ?: 0,
                classActive = classRoom.classActive,
                orgTag = classRoom.orgTag
            )
        }.sortedBy { it.name }

        val classesMap = classes.associateBy { it.id }
        val liveSessions = sessionsSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull()
            if (!session.active || expiresAt == null || expiresAt.isBefore(now)) return@mapNotNull null
            val classRoom = classesMap[session.classId] ?: return@mapNotNull null
            AdminLiveSessionOverview(
                id = node.key.orEmpty(),
                className = classRoom.name,
                subject = classRoom.subject,
                facultyName = classRoom.facultyName,
                methods = getMethods(session),
                expiresAt = session.expiresAt
            )
        }

        val todayAttendanceCount = attendanceSnapshot.children.count { node ->
            val markedAt = node.child("markedAt").getValue(String::class.java)
            val classId = node.child("classId").getValue(String::class.java)
            val classRoom = classId?.let { classesMap[it] }
            markedAt != null &&
                classRoom?.orgTag?.trim() == orgTag &&
                Instant.parse(markedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() == today
        }

        return AdminDashboardData(
            users = users,
            classes = classes,
            liveSessions = liveSessions,
            todayAttendanceCount = todayAttendanceCount
        )
    }

    suspend fun createClass(profile: UserProfile, name: String) {
        require(profile.role == "admin") { "Admin access required." }
        val payload = mapOf(
            "name" to name,
            "subject" to "",
            "facultyId" to "",
            "facultyName" to "",
            "classActive" to false,
            "orgTag" to profile.orgTag.trim()
        )
        database.child("classes").push().setValue(payload).await()
    }

    suspend fun updateAdminClassDetails(profile: UserProfile, classId: String, subject: String, facultyId: String) {
        require(profile.role == "admin") { "Admin access required." }
        val classSnapshot = database.child("classes").child(classId).get().await()
        val classRoom = classSnapshot.getValue(ClassRoom::class.java) ?: error("Class not found.")
        if (classRoom.orgTag.trim() != profile.orgTag.trim()) error("This class is outside the current admin tag.")
        val facultySnapshot = database.child("users").child(facultyId).get().await()
        val faculty = facultySnapshot.getValue(UserProfile::class.java) ?: error("Faculty not found.")
        if (faculty.role != "faculty") error("Selected account is not faculty.")
        if (faculty.orgTag.trim() != profile.orgTag.trim()) error("Faculty tag does not match this admin tag.")
        database.child("classes").child(classId).updateChildren(
            mapOf(
                "subject" to subject.trim(),
                "facultyId" to facultyId,
                "facultyName" to faculty.name
            )
        ).await()
    }

    suspend fun deleteAdminClass(profile: UserProfile, classId: String) {
        require(profile.role == "admin") { "Admin access required." }
        val classSnapshot = database.child("classes").child(classId).get().await()
        val classRoom = classSnapshot.getValue(ClassRoom::class.java) ?: error("Class not found.")
        if (classRoom.orgTag.trim() != profile.orgTag.trim()) error("This class is outside the current admin tag.")

        database.child("classes").child(classId).removeValue().await()

        val enrollmentsSnapshot = database.child("enrollments").get().await()
        enrollmentsSnapshot.children.forEach { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@forEach
            if (enrollment.classId == classId) {
                node.ref.removeValue().await()
            }
        }

        val sessionsSnapshot = database.child("sessions").get().await()
        val deletedSessionIds = mutableSetOf<String>()
        sessionsSnapshot.children.forEach { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@forEach
            if (session.classId == classId) {
                deletedSessionIds += node.key.orEmpty()
                node.ref.removeValue().await()
            }
        }

        val attendanceSnapshot = database.child("attendance").get().await()
        attendanceSnapshot.children.forEach { node ->
            val storedClassId = node.child("classId").getValue(String::class.java)
            val sessionId = node.child("sessionId").getValue(String::class.java)
            if (storedClassId == classId || sessionId in deletedSessionIds) {
                node.ref.removeValue().await()
            }
        }
    }

    suspend fun createAdminManagedAccount(
        context: Context,
        profile: UserProfile,
        name: String,
        email: String,
        password: String,
        role: String,
        rollNo: String,
        classIds: List<String>
    ) {
        require(profile.role == "admin") { "Admin access required." }
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedRole = role.trim().lowercase()
        val trimmedRollNo = rollNo.trim()
        require(trimmedName.isNotBlank()) { "Name is required." }
        require(trimmedEmail.isNotBlank()) { "Email is required." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        require(trimmedRole == "student" || trimmedRole == "faculty") { "Role must be student or faculty." }
        if (trimmedRole == "student") require(trimmedRollNo.isNotBlank()) { "Roll number is required for students." }
        if (trimmedRole == "student" && classIds.size > 1) error("A student can be assigned to only one class.")
        if (trimmedRole == "student" && classIds.isNotEmpty()) {
            val classesSnapshot = database.child("classes").get().await()
            val invalidClass = classIds.firstOrNull { classId ->
                val classRoom = classesSnapshot.child(classId).getValue(ClassRoom::class.java)
                classRoom == null || classRoom.orgTag.trim() != profile.orgTag.trim()
            }
            if (invalidClass != null) error("One or more selected classes are outside this admin tag.")
        }

        val tempAppName = "admin-create-${UUID.randomUUID()}"
        val tempApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, tempAppName)
            ?: error("Unable to initialize temporary Firebase app.")
        val secondaryAuth = FirebaseAuth.getInstance(tempApp)

        try {
            val userCredential = secondaryAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val uid = userCredential.user?.uid ?: error("Firebase user creation failed.")
            database.child("users").child(uid).setValue(
                mapOf(
                    "name" to trimmedName,
                    "email" to trimmedEmail,
                    "role" to trimmedRole,
                    "rollNo" to if (trimmedRole == "student") trimmedRollNo else "",
                    "orgTag" to profile.orgTag.trim()
                )
            ).await()
            if (trimmedRole == "student") {
                classIds.distinct().take(1).forEach { classId ->
                    database.child("enrollments").push().setValue(
                        mapOf(
                            "studentId" to uid,
                            "studentName" to trimmedName,
                            "rollNo" to trimmedRollNo,
                            "classId" to classId,
                            "enrolledAt" to Instant.now().toString()
                        )
                    ).await()
                }
            }
        } finally {
            secondaryAuth.signOut()
            tempApp.delete()
        }
    }

    suspend fun sendAdminPasswordReset(profile: UserProfile, email: String) {
        require(profile.role == "admin") { "Admin access required." }
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotBlank()) { "Email is required." }

        val userExistsInProfileStore = database.child("users").get().await().children.any { node ->
            val user = node.getValue(UserProfile::class.java)
            user?.email.equals(trimmedEmail, ignoreCase = true)
        }
        if (!userExistsInProfileStore) error("No account profile found for this email.")

        val signInMethods = auth.fetchSignInMethodsForEmail(trimmedEmail).await().signInMethods.orEmpty()
        if (signInMethods.isEmpty()) error("No Firebase sign-in account found for this email.")

        auth.sendPasswordResetEmail(trimmedEmail).await()
    }

    suspend fun deleteAdminManagedAccount(profile: UserProfile, userId: String): AdminDeleteResult {
        require(profile.role == "admin") { "Admin access required." }
        val userSnapshot = database.child("users").child(userId).get().await()
        val user = userSnapshot.getValue(UserProfile::class.java) ?: error("Account not found.")
        if (user.role == "admin") error("Admin account deletion is not allowed here.")
        if (user.orgTag.trim() != profile.orgTag.trim()) error("This account is outside the current admin tag.")

        val authDeleted = if (BuildConfig.ADMIN_API_BASE_URL.isBlank()) {
            false
        } else {
            deleteManagedAuthAccount(userId)
            true
        }

        try {
            database.child("users").child(userId).removeValue().await()

            val enrollmentsSnapshot = database.child("enrollments").get().await()
            enrollmentsSnapshot.children.forEach { node ->
                val enrollment = node.getValue(Enrollment::class.java) ?: return@forEach
                if (enrollment.studentId == userId) {
                    node.ref.removeValue().await()
                }
            }

            val attendanceSnapshot = database.child("attendance").get().await()
            attendanceSnapshot.children.forEach { node ->
                val studentId = node.child("studentId").getValue(String::class.java)
                if (studentId == userId) {
                    node.ref.removeValue().await()
                }
            }
        } catch (error: Exception) {
            if (authDeleted) {
                throw IllegalStateException(
                    "Firebase sign-in was deleted, but app data cleanup failed: ${error.message ?: "unknown error"}",
                    error
                )
            }
            throw error
        }

        return if (authDeleted) {
            AdminDeleteResult(
                authDeleted = true,
                message = "Account deleted from Firebase Auth and app data."
            )
        } else {
            AdminDeleteResult(
                authDeleted = false,
                message = "Account app data deleted. Firebase sign-in was not deleted because the admin delete API is not configured."
            )
        }
    }

    private suspend fun deleteManagedAuthAccount(userId: String) {
        val currentUser = auth.currentUser ?: error("Admin session expired.")
        val idToken = currentUser.getIdToken(false).await().token ?: error("Unable to authenticate admin request.")
        withContext(Dispatchers.IO) {
            val deleteUrl = resolveAdminDeleteUrl()
            Log.d(TAG, "Deleting managed account via $deleteUrl for userId=$userId")
            val connection = (URL(deleteUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val requestBody = JSONObject().put("userId", userId).toString()
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Log.d(TAG, "Delete API response code=$responseCode body=$responseText")

                if (responseCode !in 200..299) {
                    val message = runCatching {
                        JSONObject(responseText).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull() ?: "Unable to delete Firebase sign-in account."
                    error(message)
                }
            } catch (error: IOException) {
                Log.e(TAG, "Delete API network failure for userId=$userId", error)
                throw IllegalStateException(
                    "Unable to reach the admin delete API. ${error.message ?: "Check the server URL and backend configuration."}",
                    error
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun resolveAdminDeleteUrl(): String {
        val overrideBaseUrl = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')
        if (overrideBaseUrl.isNotBlank()) {
            return "$overrideBaseUrl/api/admin/delete-user"
        }

        val projectId = FirebaseApp.getInstance().options.projectId?.trim().orEmpty()
        if (projectId.isBlank()) {
            error("Firebase project ID is not available for admin deletion.")
        }

        val region = BuildConfig.ADMIN_DELETE_FUNCTION_REGION.trim().ifBlank { "us-central1" }
        return "https://$region-$projectId.cloudfunctions.net/deleteAdminManagedUser"
    }

    suspend fun markFingerprintAttendance(profile: UserProfile, sessionId: String) {
        val (id, session) = getValidatedSessionById(profile, sessionId)
        if ("fingerprint" !in getMethods(session)) error("Fingerprint not allowed.")
        recordAttendance(profile, id, session, "fingerprint", "device")
    }

    suspend fun markQrAttendance(profile: UserProfile, rawValue: String) {
        val token = extractQrToken(rawValue)
        val (id, session) = getValidatedSessionByToken(profile, token)
        if ("qr" !in getMethods(session)) error("QR not allowed.")
        recordAttendance(profile, id, session, "qr", "app")
    }

    suspend fun markFaceAttendance(profile: UserProfile, sessionId: String, width: Int, height: Int, signature: String) {
        val (id, session) = getValidatedSessionById(profile, sessionId)
        if ("face" !in getMethods(session)) error("Face not allowed.")
        
        val biometrics = getBiometricProfile(profile)
        val registeredSignature = biometrics.face?.signature ?: error("No face registered.")
        if (!faceSignatureMatches(registeredSignature, signature)) error("Face mismatch.")
        
        recordAttendance(profile, id, session, "face", "app", mapOf(
            "faceCapture" to mapOf("width" to width, "height" to height)
        ))
    }

    suspend fun getAdminAttendanceAnalytics(profile: UserProfile): AdminAttendanceAnalytics {
        require(profile.role == "admin") { "Admin access required." }
        val orgTag = profile.orgTag.trim()

        val classesSnapshot = database.child("classes").get().await()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val sessionsSnapshot = database.child("sessions").get().await()
        val attendanceSnapshot = database.child("attendance").get().await()

        val enrollmentsByClass = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            enrollment.classId to enrollment.studentId
        }.groupBy({ it.first }, { it.second })

        val attendanceBySession = attendanceSnapshot.children.mapNotNull { node ->
            val sessionId = node.child("sessionId").getValue(String::class.java) ?: return@mapNotNull null
            val studentId = node.child("studentId").getValue(String::class.java) ?: return@mapNotNull null
            sessionId to studentId
        }.groupBy({ it.first }, { it.second })

        val sessionsByClass = sessionsSnapshot.children.mapNotNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@mapNotNull null
            node.key.orEmpty() to session
        }.groupBy({ it.second.classId }, { it.first to it.second })

        val classAnalytics = classesSnapshot.children.mapNotNull { node ->
            val classId = node.key.orEmpty()
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != orgTag) return@mapNotNull null
            val enrolledStudents = enrollmentsByClass[classId].orEmpty().toSet()
            val classSessions = sessionsByClass[classId].orEmpty()
            val totalStudents = enrolledStudents.size
            val totalSessions = classSessions.size
            val totalSlots = totalStudents * totalSessions
            val totalPresent = classSessions.sumOf { sessionPair ->
                val sessionId = sessionPair.first
                val presentStudents = attendanceBySession[sessionId] ?: emptyList<String>()
                presentStudents
                    .distinct()
                    .filter { studentId -> enrolledStudents.contains(studentId) }
                    .size
            }
            val totalAbsent = (totalSlots - totalPresent).coerceAtLeast(0)
            val percentage = if (totalSlots == 0) 0 else ((totalPresent * 100f) / totalSlots).toInt()

            AdminAttendanceClassAnalytics(
                classId = classId,
                className = classRoom.name,
                subject = classRoom.subject,
                totalSessions = totalSessions,
                totalStudents = totalStudents,
                totalPresent = totalPresent,
                totalAbsent = totalAbsent,
                percentage = percentage
            )
        }.sortedBy { it.className }

        return AdminAttendanceAnalytics(classes = classAnalytics)
    }

    suspend fun getAdminClassDetail(profile: UserProfile, classId: String): AdminClassDetail {
        require(profile.role == "admin") { "Admin access required." }

        val classSnapshot = database.child("classes").child(classId).get().await()
        val classRoom = classSnapshot.getValue(ClassRoom::class.java) ?: error("Class not found.")
        if (classRoom.orgTag.trim() != profile.orgTag.trim()) error("This class is outside the current admin tag.")
        val usersSnapshot = database.child("users").get().await()
        val enrollmentsSnapshot = database.child("enrollments").get().await()

        val enrolledStudentIds = enrollmentsSnapshot.children.mapNotNull { node ->
            val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
            enrollment.studentId.takeIf { enrollment.classId == classId }
        }.toSet()

        val students = usersSnapshot.children.mapNotNull { node ->
            val user = node.getValue(UserProfile::class.java) ?: return@mapNotNull null
            if (user.role != "student") return@mapNotNull null
            if (user.orgTag.trim() != profile.orgTag.trim()) return@mapNotNull null
            AdminStudentOption(
                id = node.key.orEmpty(),
                name = user.name,
                email = user.email,
                rollNo = user.rollNo,
                enrolled = node.key.orEmpty() in enrolledStudentIds
            )
        }.sortedWith(compareByDescending<AdminStudentOption> { it.enrolled }.thenBy { it.name })

        return AdminClassDetail(
            classId = classId,
            className = classRoom.name,
            subject = classRoom.subject,
            facultyId = classRoom.facultyId,
            facultyName = classRoom.facultyName,
            students = students
        )
    }

    suspend fun adminAssignStudentToClass(profile: UserProfile, classId: String, studentId: String) {
        require(profile.role == "admin") { "Admin access required." }
        require(classId.isNotBlank()) { "Class is required." }
        require(studentId.isNotBlank()) { "Student is required." }

        val classSnapshot = database.child("classes").child(classId).get().await()
        val classRoom = classSnapshot.getValue(ClassRoom::class.java) ?: error("Class not found.")
        if (classRoom.orgTag.trim() != profile.orgTag.trim()) error("This class is outside the current admin tag.")

        val userSnapshot = database.child("users").child(studentId).get().await()
        val student = userSnapshot.getValue(UserProfile::class.java) ?: error("Student not found.")
        if (student.role != "student") error("Selected user is not a student account.")
        if (student.orgTag.trim() != profile.orgTag.trim()) error("Student tag does not match this admin tag.")

        val existingEnrollment = database.child("enrollments").get().await().children.firstOrNull { node ->
            val enrollment = node.getValue(Enrollment::class.java)
            enrollment?.studentId == studentId
        }
        if (existingEnrollment != null) {
            val currentClassId = existingEnrollment.getValue(Enrollment::class.java)?.classId.orEmpty()
            if (currentClassId == classId) return
            error("This student is already assigned to another class.")
        }

        database.child("enrollments").push().setValue(
            mapOf(
                "studentId" to studentId,
                "studentName" to student.name,
                "rollNo" to student.rollNo,
                "classId" to classId,
                "enrolledAt" to Instant.now().toString()
            )
        ).await()
    }

    suspend fun getFacultyStudentGroups(profile: UserProfile): List<ClassStudentGroup> {
        val classes = getFacultyClasses(profile)
        if (classes.isEmpty()) return emptyList()

        val usersSnapshot = database.child("users").get().await()
        val enrollmentsSnapshot = database.child("enrollments").get().await()
        val userMap = usersSnapshot.children.associate { node ->
            node.key.orEmpty() to (node.getValue(UserProfile::class.java) ?: UserProfile())
        }

        return classes.map { classItem ->
            val students = enrollmentsSnapshot.children.mapNotNull { node ->
                val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
                if (enrollment.classId != classItem.id) return@mapNotNull null
                val user = userMap[enrollment.studentId] ?: return@mapNotNull null
                if (user.orgTag.trim() != profile.orgTag.trim()) return@mapNotNull null
                StudentRosterItem(
                    id = enrollment.studentId,
                    name = enrollment.studentName.ifBlank { user.name },
                    email = user.email,
                    rollNo = enrollment.rollNo.ifBlank { user.rollNo }
                )
            }.sortedBy { it.rollNo }

            ClassStudentGroup(
                classId = classItem.id,
                className = classItem.name,
                subject = classItem.subject,
                students = students
            )
        }.sortedBy { it.className }
    }

    suspend fun getAdminStudentGroups(profile: UserProfile): List<ClassStudentGroup> {
        require(profile.role == "admin") { "Admin access required." }
        val classesSnapshot = database.child("classes").get().await()
        val usersSnapshot = database.child("users").get().await()
        val enrollmentsSnapshot = database.child("enrollments").get().await()

        val classes = classesSnapshot.children.mapNotNull { node ->
            val classRoom = node.getValue(ClassRoom::class.java) ?: return@mapNotNull null
            if (classRoom.orgTag.trim() != profile.orgTag.trim()) return@mapNotNull null
            Triple(node.key.orEmpty(), classRoom.name, classRoom.subject)
        }.sortedBy { it.second }

        val userMap = usersSnapshot.children.associate { node ->
            node.key.orEmpty() to (node.getValue(UserProfile::class.java) ?: UserProfile())
        }

        return classes.map { (classId, className, subject) ->
            val students = enrollmentsSnapshot.children.mapNotNull { node ->
                val enrollment = node.getValue(Enrollment::class.java) ?: return@mapNotNull null
                if (enrollment.classId != classId) return@mapNotNull null
                val user = userMap[enrollment.studentId] ?: return@mapNotNull null
                if (user.role != "student" || user.orgTag.trim() != profile.orgTag.trim()) return@mapNotNull null
                StudentRosterItem(
                    id = enrollment.studentId,
                    name = enrollment.studentName.ifBlank { user.name },
                    email = user.email,
                    rollNo = enrollment.rollNo.ifBlank { user.rollNo }
                )
            }.sortedBy { it.rollNo }

            ClassStudentGroup(
                classId = classId,
                className = className,
                subject = subject,
                students = students
            )
        }
    }

    private fun getMethods(session: SessionRecord): List<String> {
        val allowed = session.allowedMethods.orEmpty().filter { it.isNotBlank() }
        if (allowed.isNotEmpty()) return allowed
        return when (session.attendanceMethod) {
            "all" -> listOf("qr", "fingerprint", "face")
            null, "" -> listOf("qr")
            else -> listOf(session.attendanceMethod)
        }
    }

    private fun roleSortOrder(role: String): Int {
        return when (role.lowercase()) {
            "admin" -> 0
            "faculty" -> 1
            "student" -> 2
            else -> 3
        }
    }

    private fun faceSignatureMatches(registered: String, current: String): Boolean {
        if (registered.length != current.length || registered.isBlank()) return false
        val distance = registered.zip(current).count { (left, right) -> left != right }
        return distance <= 18
    }

    private suspend fun getValidatedSessionById(
        profile: UserProfile,
        sessionId: String
    ): Pair<String, SessionRecord> {
        val sessionSnapshot = database.child("sessions").child(sessionId).get().await()
        val session = sessionSnapshot.getValue(SessionRecord::class.java)
            ?: error("Session not found.")
        validateSession(profile, sessionId, session)
        return sessionId to session
    }

    private suspend fun getValidatedSessionByToken(
        profile: UserProfile,
        token: String
    ): Pair<String, SessionRecord> {
        val sessionsSnapshot = database.child("sessions").get().await()
        val match = sessionsSnapshot.children.firstNotNullOfOrNull { node ->
            val session = node.getValue(SessionRecord::class.java) ?: return@firstNotNullOfOrNull null
            val sessionToken = session.token ?: return@firstNotNullOfOrNull null
            if (sessionToken == token) node.key.orEmpty() to session else null
        } ?: error("This QR code is not recognized.")
        validateSession(profile, match.first, match.second)
        return match
    }

    private suspend fun validateSession(profile: UserProfile, sessionId: String, session: SessionRecord) {
        if (!session.active) error("Session is no longer active.")
        if (session.expiresAt.isBlank()) error("Session has no expiry time.")
        val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull()
            ?: error("Session expiry is invalid.")
        if (expiresAt.isBefore(Instant.now())) error("Session expired.")

        val classSnapshot = database.child("classes").child(session.classId).get().await()
        val classRoom = classSnapshot.getValue(ClassRoom::class.java) ?: error("Class not found.")
        if (classRoom.orgTag.trim() != profile.orgTag.trim()) {
            error("This class belongs to a different organization tag.")
        }

        val enrollmentSnapshot = database.child("enrollments").get().await()
        val enrolled = enrollmentSnapshot.children.any { node ->
            val enrollment = node.getValue(Enrollment::class.java)
            enrollment != null && enrollment.studentId == profile.uid && enrollment.classId == session.classId
        }
        if (!enrolled) error("You are not enrolled in this class.")

        val attendanceSnapshot = database.child("attendance").get().await()
        val alreadyMarked = attendanceSnapshot.children.any { node ->
            val studentId = node.child("studentId").getValue(String::class.java)
            val storedSessionId = node.child("sessionId").getValue(String::class.java)
            studentId == profile.uid && storedSessionId == sessionId
        }
        if (alreadyMarked) error("Attendance already recorded for this session.")
    }

    private suspend fun recordAttendance(
        profile: UserProfile,
        sessionId: String,
        session: SessionRecord,
        method: String,
        verifiedBy: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val payload = mutableMapOf<String, Any?>(
            "sessionId" to sessionId,
            "studentId" to profile.uid,
            "studentName" to profile.name,
            "rollNo" to profile.rollNo,
            "classId" to session.classId,
            "markedAt" to Instant.now().toString(),
            "method" to method,
            "verifiedBy" to verifiedBy
        )
        payload.putAll(extra)
        database.child("attendance").push().setValue(payload).await()
    }

    private fun extractQrToken(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) error("QR code is empty.")
        val tokenFromUrl = runCatching {
            val uri = URI(trimmed)
            uri.rawQuery
                ?.split("&")
                ?.mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0] == "token") parts[1] else null
                }
                ?.firstOrNull()
        }.getOrNull()
        return tokenFromUrl ?: trimmed
    }
}
