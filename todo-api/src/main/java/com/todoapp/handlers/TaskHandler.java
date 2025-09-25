package com.todoapp.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todoapp.models.Task;
import com.todoapp.models.CreateTaskRequest;
import com.todoapp.models.UpdateTaskRequest;
import com.todoapp.services.TaskService;
import com.todoapp.utils.UUIDGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class TaskHandler {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskHandler() {
        this.taskService = new TaskService();
        this.objectMapper = new ObjectMapper();
    }

    public APIGatewayProxyResponseEvent createTask(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");
            String userEmail = claims.get("email");

            CreateTaskRequest createRequest = objectMapper.readValue(request.getBody(), CreateTaskRequest.class);

            Task task = new Task();
            task.setTaskId(UUIDGenerator.generateUUID());
            task.setUserId(userId);
            task.setUserEmail(userEmail);
            task.setDescription(createRequest.getDescription());
            task.setDate(createRequest.getDate());
            task.setStatus("Pending");
            task.setCreatedAt(System.currentTimeMillis());
            task.setDeadline(System.currentTimeMillis() + 5 * 60 * 1000); // 5 minutes from now

            Task createdTask = taskService.createTask(task);

            return createResponse(200, createdTask);
        } catch (Exception e) {
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }

    public APIGatewayProxyResponseEvent getTasks(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");

            if (userId == null || userId.trim().isEmpty()) {
                return createResponse(400, Map.of("error", "User ID not found in token."));
            }

            context.getLogger().log("Fetching tasks for userId: " + userId);
            List<Task> tasks = taskService.getTasksByUser(userId);

            return createResponse(200, tasks);
        } catch (Exception e) {
            context.getLogger().log("Error getting tasks: " + e.getMessage());
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }

    public APIGatewayProxyResponseEvent updateTask(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");

            String taskId = request.getPathParameters().get("taskId");

            Task existingTask = taskService.getTask(userId, taskId);
            if (existingTask == null) {
                return createResponse(404, Map.of("error", "Task not found"));
            }

            UpdateTaskRequest updateRequest = objectMapper.readValue(request.getBody(), UpdateTaskRequest.class);
            if (updateRequest.getDescription() != null) {
                existingTask.setDescription(updateRequest.getDescription());
            }
            if (updateRequest.getDate() != null) {
                existingTask.setDate(updateRequest.getDate());
            }
            if (updateRequest.getStatus() != null) {
                existingTask.setStatus(updateRequest.getStatus());
            }

            Task updatedTask = taskService.updateTask(existingTask);

            return createResponse(200, updatedTask);
        } catch (Exception e) {
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }

    public APIGatewayProxyResponseEvent deleteTask(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");

            String taskId = request.getPathParameters().get("taskId");

            taskService.deleteTask(userId, taskId);

            return createResponse(200, Map.of("message", "Task deleted successfully"));
        } catch (Exception e) {
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        try {
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            response.setBody(objectMapper.writeValueAsString(body));

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Requested-With");
            headers.put("Access-Control-Allow-Credentials", "true");

            response.setHeaders(headers);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}