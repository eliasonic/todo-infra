package com.todoapp.services;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.regions.Region;
import com.todoapp.models.Task;
import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBService {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Task> taskTable;

    public DynamoDBService() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.EU_CENTRAL_1)
                .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        this.taskTable = enhancedClient.table("TodoTasks",
                TableSchema.fromBean(Task.class));
    }

    public Task createTask(Task task) {
        taskTable.putItem(task);
        return task;
    }

    public List<Task> getTasksByUser(String userId) {
        return taskTable.query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))
                )
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public Task updateTask(Task task) {
        return taskTable.updateItem(task);
    }

    public void deleteTask(String userId, String taskId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(taskId)
                .build();
        taskTable.deleteItem(key);
    }

    public Task getTask(String userId, String taskId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(taskId)
                .build();
        return taskTable.getItem(key);
    }
}