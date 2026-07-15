package dev.brahmkshatriya.echo.extension.loader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.extension.loader.db.models.CurrentUser
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM UserEntity WHERE type = :type AND extId = :extId AND id = :userId")
    suspend fun getUser(type: ExtensionType, extId: String, userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM CurrentUser")
    fun observeCurrentUser(): Flow<List<CurrentUser>>

    @Query("SELECT ue.* FROM UserEntity ue INNER JOIN CurrentUser cu ON ue.type = cu.type AND ue.extId = cu.extId AND ue.id = cu.userId")
    fun observeActiveUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCurrentUser(currentUser: CurrentUser)

    @Query("DELETE FROM CurrentUser WHERE type = :type AND extId = :extId")
    suspend fun deleteCurrentUser(type: ExtensionType, extId: String)

    @Query("DELETE FROM UserEntity WHERE type = :type AND extId = :extId")
    suspend fun deleteUsersForExtension(type: ExtensionType, extId: String)

}
