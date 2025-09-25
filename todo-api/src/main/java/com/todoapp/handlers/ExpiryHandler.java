package com.todoapp.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.todoapp.services.TaskService;
import com.todoapp.services.NotificationService;
import com.todoapp.models.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.regions.Region;

import java.util.Map;

public class ExpiryHandler {
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public ExpiryHandler() {
        this.taskService = new TaskService();
        this.notificationService = new NotificationService(System.getenv("SNS_TOPIC_ARN"));
        this.objectMapper = new ObjectMapper();
        this.queueUrl = System.getenv("SQS_QUEUE_URL");
    }

    public Void processStream(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            if (record.getEventName().equals("INSERT")) {
                // New task created - schedule expiry check
                Task newTask = convertToTask(record.getDynamodb().getNewImage());
                if ("Pending".equals(newTask.getStatus())) {
                    scheduleExpiryCheck(newTask);
                }
            } else if (record.getEventName().equals("MODIFY")) {
                // Task updated - check if status changed from Pending
                Task oldTask = convertToTask(record.getDynamodb().getOldImage());
                Task newTask = convertToTask(record.getDynamodb().getNewImage());

                if ("Pending".equals(oldTask.getStatus()) &&
                        !"Pending".equals(newTask.getStatus())) {
                    // Task completed or deleted - cancel expiry
                    cancelExpiryCheck(oldTask);
                }
            }
        }
        return null;
    }

    public Void handleExpiry(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                ExpiryMessage expiryMessage = objectMapper.readValue(message.getBody(), ExpiryMessage.class);
                Task task = taskService.getTask(expiryMessage.getUserId(), expiryMessage.getTaskId());

                if (task != null && "Pending".equals(task.getStatus())) {
                    // Mark as expired
                    task.setStatus("Expired");
                    taskService.updateTask(task);

                    notificationService.sendTaskExpiryNotification(task, expiryMessage.getUserEmail());
                }
            } catch (Exception e) {
                context.getLogger().log("Error processing expiry: " + e.getMessage());
            }
        }
        return null;
    }

    private void scheduleExpiryCheck(Task task) {
        try {
            ExpiryMessage message = new ExpiryMessage(task.getUserId(), task.getTaskId(), task.getUserEmail() );
            String messageBody = objectMapper.writeValueAsString(message);

            SqsClient sqsClient = SqsClient.builder()
                    .region(Region.EU_CENTRAL_1)
                    .build();

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(task.getTaskId())
                    .messageDeduplicationId(task.getTaskId() + "-" + task.getDeadline())
                    .build();

            // Set delay to match task deadline
            long delaySeconds = (task.getDeadline() - System.currentTimeMillis()) / 1000;
            if (delaySeconds > 0 && delaySeconds <= 900) { // Max 15 minutes
                sendMessageRequest = sendMessageRequest.toBuilder()
                        .delaySeconds((int) delaySeconds)
                        .build();
            }

            sqsClient.sendMessage(sendMessageRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to schedule expiry check", e);
        }
    }

    private void cancelExpiryCheck(Task task) {
        // In FIFO queues, we can't directly cancel messages
        // The handler will check if task is still pending before processing
        // This ensures exactly-once processing
    }

    private Task convertToTask(Map<String, AttributeValue> image) {
        Task task = new Task();

        if (image.get("userId") != null && image.get("userId").getS() != null) {
            task.setUserId(image.get("userId").getS());
        }
        if (image.get("taskId") != null && image.get("taskId").getS() != null) {
            task.setTaskId(image.get("taskId").getS());
        }
        if (image.get("description") != null && image.get("description").getS() != null) {
            task.setDescription(image.get("description").getS());
        }
        if (image.get("date") != null && image.get("date").getS() != null) {
            task.setDate(image.get("date").getS());
        }
        if (image.get("status") != null && image.get("status").getS() != null) {
            task.setStatus(image.get("status").getS());
        }
        if (image.get("deadline") != null && image.get("deadline").getN() != null) {
            task.setDeadline(Long.parseLong(image.get("deadline").getN()));
        }
        if (image.get("createdAt") != null && image.get("createdAt").getN() != null) {
            task.setCreatedAt(Long.parseLong(image.get("createdAt").getN()));
        }

        return task;
    }

    private static class ExpiryMessage {
        private String userId;
        private String taskId;
        private String userEmail;

        public ExpiryMessage() {}

        public ExpiryMessage(String userId, String taskId, String userEmail) {
            this.userId = userId;
            this.taskId = taskId;
            this.userEmail = userEmail;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    }
}