package com.todoapp.services;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.regions.Region;
import com.todoapp.models.Task;

public class NotificationService {
    private final SnsClient snsClient;
    private final String topicArn;

    public NotificationService(String topicArn) {
        this.snsClient = SnsClient.builder()
                .region(Region.EU_CENTRAL_1)
                .build();
        this.topicArn = topicArn;
    }

    public void subscribeUser(String email) {
        SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("email")
                .endpoint(email)
                .build();
        snsClient.subscribe(subscribeRequest);
    }

    public void sendTaskExpiryNotification(Task task, String userEmail) {
        String message = String.format(
                "Task Expired: %s\nDescription: %s\nDate: %s\nDeadline: %s",
                task.getTaskId(), task.getDescription(), task.getDate(),
                new java.util.Date(task.getDeadline()).toString()
        );

        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(topicArn)
                .message(message)
                .subject("Task Expiry Notification")
                .build();

        snsClient.publish(publishRequest);
    }
}