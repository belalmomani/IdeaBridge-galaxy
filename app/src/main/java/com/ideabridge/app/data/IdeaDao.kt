package com.ideabridge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IdeaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdea(idea: IdeaEntity): Long

    @Query("UPDATE ideas SET title = :title, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateIdea(id: Long, title: String, description: String?, updatedAt: Long): Int

    @Query("DELETE FROM relations WHERE firstIdeaId = :id OR secondIdeaId = :id")
    suspend fun deleteRelationsOf(id: Long): Int

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun deleteIdea(id: Long): Int

    @Query(
        "SELECT * FROM ideas WHERE title LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "OR IFNULL(description, '') LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY updatedAt DESC, id DESC"
    )
    fun observe(pattern: String): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    fun observeById(id: Long): Flow<IdeaEntity?>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getById(id: Long): IdeaEntity?

    @Query("SELECT * FROM ideas ORDER BY id")
    suspend fun getAllIdeas(): List<IdeaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRelation(relation: RelationEntity): Long

    @Query("SELECT COUNT(*) FROM relations WHERE firstIdeaId = :first AND secondIdeaId = :second AND type = :type")
    suspend fun countRelation(first: Long, second: Long, type: RelationType): Int

    @Query("DELETE FROM relations WHERE id = :id")
    suspend fun deleteRelation(id: Long): Int

    @Query("SELECT * FROM relations ORDER BY id")
    suspend fun getAllRelations(): List<RelationEntity>

    @Query(
        "SELECT r.id AS id, r.firstIdeaId AS firstIdeaId, r.secondIdeaId AS secondIdeaId, " +
            "r.type AS type, r.createdAt AS createdAt, a.title AS firstTitle, b.title AS secondTitle " +
            "FROM relations r JOIN ideas a ON a.id = r.firstIdeaId JOIN ideas b ON b.id = r.secondIdeaId " +
            "WHERE r.firstIdeaId = :ideaId OR r.secondIdeaId = :ideaId ORDER BY r.createdAt DESC, r.id DESC"
    )
    fun observeRelationsOf(ideaId: Long): Flow<List<RelationRow>>

    // ---------- المجرات ----------
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGalaxy(galaxy: GalaxyEntity): Long

    @Query("UPDATE galaxies SET name = :name, colorIndex = :colorIndex WHERE id = :id")
    suspend fun updateGalaxy(id: Long, name: String, colorIndex: Int): Int

    @Query("DELETE FROM galaxies WHERE id = :id")
    suspend fun deleteGalaxy(id: Long): Int

    @Query("DELETE FROM ideas WHERE galaxyId = :galaxyId")
    suspend fun deleteIdeasOfGalaxy(galaxyId: Long): Int

    @Query(
        "DELETE FROM relations WHERE firstIdeaId IN (SELECT id FROM ideas WHERE galaxyId = :galaxyId) " +
            "OR secondIdeaId IN (SELECT id FROM ideas WHERE galaxyId = :galaxyId)"
    )
    suspend fun deleteRelationsOfGalaxy(galaxyId: Long): Int

    @Query("SELECT * FROM galaxies WHERE id = :id")
    suspend fun getGalaxy(id: Long): GalaxyEntity?

    @Query("SELECT * FROM galaxies WHERE id = :id")
    fun observeGalaxyById(id: Long): Flow<GalaxyEntity?>

    @Query("SELECT id FROM galaxies ORDER BY id LIMIT 1")
    suspend fun firstGalaxyId(): Long?

    @Query("SELECT * FROM galaxies ORDER BY id")
    suspend fun getAllGalaxies(): List<GalaxyEntity>

    @Query(
        "SELECT g.id AS id, g.name AS name, g.colorIndex AS colorIndex, g.createdAt AS createdAt, " +
            "(SELECT COUNT(*) FROM ideas i WHERE i.galaxyId = g.id) AS noteCount FROM galaxies g ORDER BY g.id"
    )
    fun observeGalaxyRows(): Flow<List<GalaxyRow>>

    @Query(
        "SELECT * FROM ideas WHERE galaxyId = :galaxyId AND (title LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "OR IFNULL(description, '') LIKE '%' || :pattern || '%' ESCAPE '\\') " +
            "ORDER BY updatedAt DESC, id DESC"
    )
    fun observeInGalaxy(galaxyId: Long, pattern: String): Flow<List<IdeaEntity>>
}
