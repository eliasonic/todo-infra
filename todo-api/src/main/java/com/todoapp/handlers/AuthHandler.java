package com.todoapp.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostAuthenticationEvent;
import com.todoapp.services.NotificationService;

public class AuthHandler implements RequestHandler<CognitoUserPoolPostAuthenticationEvent, CognitoUserPoolPostAuthenticationEvent> {
    private final NotificationService notificationService;

    public AuthHandler() {
        String topicArn = System.getenv("SNS_TOPIC_ARN");
        this.notificationService = new NotificationService(topicArn);
    }

    @Override
    public CognitoUserPoolPostAuthenticationEvent handleRequest(CognitoUserPoolPostAuthenticationEvent event, Context context) {
        try {
            if ("PostAuthentication_Authentication".equals(event.getTriggerSource())) {
                String userEmail = event.getRequest().getUserAttributes().get("email");
                notificationService.subscribeUser(userEmail);
            }
        } catch (Exception e) {
            context.getLogger().log("Error in post authentication: " + e.getMessage());
        }

        return event;
    }
}
