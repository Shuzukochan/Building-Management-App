package com.app.buildingmanagement.repositories

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.app.buildingmanagement.config.FirebaseConfig
import com.app.buildingmanagement.models.User
import com.app.buildingmanagement.models.UserStatus
import com.app.buildingmanagement.models.UserRole
import java.util.Date

/**
 * User Repository
 * 
 * Handles data access operations for User entities.
 * Implements Repository pattern for user management operations including authentication.
 */
class UserRepository {
    
    private val auth = FirebaseConfig.auth
    private val database = FirebaseConfig.database
    private val usersRef = database.reference.child(FirebaseConfig.DatabasePaths.USERS)
    
    /**
     * Get all users
     */
    suspend fun getAllUsers(): List<User> {
        return try {
            val snapshot = usersRef.get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(User::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get users flow for real-time updates
     */
    fun getUsersFlow(): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { child ->
                    child.getValue(User::class.java)
                }
                trySend(users)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        usersRef.addValueEventListener(listener)
        
        awaitClose {
            usersRef.removeEventListener(listener)
        }
    }
    
    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            snapshot.getValue(User::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get current user
     */
    suspend fun getCurrentUser(): User? {
        return try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                getUserById(currentUserId)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get user by email
     */
    suspend fun getUserByEmail(email: String): User? {
        return try {
            val snapshot = usersRef.orderByChild("email").equalTo(email).get().await()
            snapshot.children.firstOrNull()?.getValue(User::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get user by phone number
     */
    suspend fun getUserByPhoneNumber(phoneNumber: String): User? {
        return try {
            val snapshot = usersRef.orderByChild("phoneNumber").equalTo(phoneNumber).get().await()
            snapshot.children.firstOrNull()?.getValue(User::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get users by role
     */
    suspend fun getUsersByRole(role: String): List<User> {
        return try {
            val snapshot = usersRef.orderByChild("role").equalTo(role).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(User::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get users by status
     */
    suspend fun getUsersByStatus(status: String): List<User> {
        return try {
            val snapshot = usersRef.orderByChild("status").equalTo(status).get().await()
            snapshot.children.mapNotNull { child ->
                child.getValue(User::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get active tenants
     */
    suspend fun getActiveTenants(): List<User> {
        return getUsersByRole(UserRole.TENANT).filter { it.status == UserStatus.ACTIVE }
    }
    
    /**
     * Get staff members
     */
    suspend fun getStaffMembers(): List<User> {
        return getAllUsers().filter { it.isStaff() && it.status == UserStatus.ACTIVE }
    }
    
    /**
     * Create new user account
     */
    suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
        user: User
    ): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid
            
            if (userId != null) {
                val newUser = user.copy(
                    id = userId,
                    email = email,
                    createdDate = Date(),
                    status = UserStatus.ACTIVE
                )
                
                val success = createUserProfile(newUser)
                if (success) {
                    Result.success(newUser)
                } else {
                    Result.failure(Exception("Failed to create user profile"))
                }
            } else {
                Result.failure(Exception("Failed to create user account"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid
            
            if (userId != null) {
                val user = getUserById(userId)
                if (user != null) {
                    // Update last login date
                    updateLastLoginDate(userId)
                    Result.success(user)
                } else {
                    Result.failure(Exception("User profile not found"))
                }
            } else {
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with phone credential
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<User> {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val userId = authResult.user?.uid
            
            if (userId != null) {
                val user = getUserById(userId)
                if (user != null) {
                    updateLastLoginDate(userId)
                    Result.success(user)
                } else {
                    Result.failure(Exception("User profile not found"))
                }
            } else {
                Result.failure(Exception("Phone authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create user profile in database
     */
    suspend fun createUserProfile(user: User): Boolean {
        return try {
            usersRef.child(user.id).setValue(user).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update user profile
     */
    suspend fun updateUserProfile(user: User): Boolean {
        return try {
            usersRef.child(user.id).setValue(user).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update user status
     */
    suspend fun updateUserStatus(userId: String, status: String): Boolean {
        return try {
            usersRef.child(userId).child("status").setValue(status).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update user role
     */
    suspend fun updateUserRole(userId: String, role: String): Boolean {
        return try {
            usersRef.child(userId).child("role").setValue(role).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update last login date
     */
    private suspend fun updateLastLoginDate(userId: String): Boolean {
        return try {
            usersRef.child(userId).child("lastLoginDate").setValue(Date().time).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update email verification status
     */
    suspend fun updateEmailVerificationStatus(userId: String, isVerified: Boolean): Boolean {
        return try {
            usersRef.child(userId).child("isEmailVerified").setValue(isVerified).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update phone verification status
     */
    suspend fun updatePhoneVerificationStatus(userId: String, isVerified: Boolean): Boolean {
        return try {
            usersRef.child(userId).child("isPhoneVerified").setValue(isVerified).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete user account
     */
    suspend fun deleteUser(userId: String): Boolean {
        return try {
            // Delete from database
            usersRef.child(userId).removeValue().await()
            
            // Delete from authentication (only if current user)
            if (auth.currentUser?.uid == userId) {
                auth.currentUser?.delete()?.await()
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Change user password
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        return try {
            val user = auth.currentUser
            if (user != null && user.email != null) {
                // Re-authenticate user
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                user.reauthenticate(credential).await()
                
                // Update password
                user.updatePassword(newPassword).await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Reset password
     */
    suspend fun resetPassword(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Search users
     */
    suspend fun searchUsers(
        query: String,
        role: String? = null,
        status: String? = null
    ): List<User> {
        return try {
            val allUsers = getAllUsers()
            
            allUsers.filter { user ->
                val matchesQuery = query.isEmpty() ||
                    user.fullName.contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true) ||
                    user.phoneNumber.contains(query, ignoreCase = true)
                
                val matchesRole = role == null || user.role == role
                val matchesStatus = status == null || user.status == status
                
                matchesQuery && matchesRole && matchesStatus
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
    }
    
    // Statistics methods
    
    /**
     * Get total users count
     */
    suspend fun getTotalUsersCount(): Int {
        return try {
            val snapshot = usersRef.get().await()
            snapshot.childrenCount.toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get active users count
     */
    suspend fun getActiveUsersCount(): Int {
        return getUsersByStatus(UserStatus.ACTIVE).size
    }
    
    /**
     * Get tenants count
     */
    suspend fun getTenantsCount(): Int {
        return getUsersByRole(UserRole.TENANT).size
    }
    
    /**
     * Get new registrations count for period
     */
    suspend fun getNewRegistrationsCount(startDate: Date, endDate: Date): Int {
        return try {
            getAllUsers().count { user ->
                !user.createdDate.before(startDate) && !user.createdDate.after(endDate)
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get user statistics by role
     */
    suspend fun getUserStatsByRole(): Map<String, Int> {
        return try {
            getAllUsers().groupBy { it.role }
                .mapValues { (_, users) -> users.size }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Get user verification statistics
     */
    suspend fun getUserVerificationStats(): Map<String, Int> {
        return try {
            val allUsers = getAllUsers()
            mapOf(
                "total" to allUsers.size,
                "email_verified" to allUsers.count { it.isEmailVerified },
                "phone_verified" to allUsers.count { it.isPhoneVerified },
                "fully_verified" to allUsers.count { it.isVerified() }
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}