package com.todoapp.models;

public class CreateTaskRequest {
    private String description;
    private String date;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}