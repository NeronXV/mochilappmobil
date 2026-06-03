package com.mochilapp.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<Post>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post)

    @Query("UPDATE posts SET likes = likes + 1, isLiked = 1 WHERE id = :postId")
    suspend fun likePost(postId: Int)

    @Query("UPDATE posts SET likes = likes - 1, isLiked = 0 WHERE id = :postId")
    suspend fun unlikePost(postId: Int)
}
