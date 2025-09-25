package com.todoapp.services;

import com.todoapp.models.Task;
import java.util.List;

public class TaskService {
    private final DynamoDBService dynamoDBService;

    public TaskService() {
        this.dynamoDBService = new DynamoDBService();
    }

    public Task createTask(Task task) {
        return dynamoDBService.createTask(task);
    }

    public List<Task> getTasksByUser(String userId) {
        return dynamoDBService.getTasksByUser(userId);
    }

    public Task updateTask(Task task) {
        return dynamoDBService.updateTask(task);
    }

    public void deleteTask(String userId, String taskId) {
        dynamoDBService.deleteTask(userId, taskId);
    }

    public Task getTask(String userId, String taskId) {
        return dynamoDBService.getTask(userId, taskId);
    }
}