package com.example.smartphoneagent.task

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksByStatus(status: String): Flow<List<TaskEntity>> {
        return taskDao.getTasksByStatus(status)
    }

    suspend fun getTask(id: Long): TaskEntity? {
        return taskDao.getTaskById(id)
    }

    suspend fun insert(task: TaskEntity): Long {
        return taskDao.insertTask(task)
    }

    suspend fun update(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    suspend fun delete(id: Long) {
        taskDao.deleteTask(id)
    }

    suspend fun deleteCompleted() {
        taskDao.deleteCompletedTasks()
    }
}
