package com.cubapincha.job.data;

import com.cubapincha.job.model.AppNotification;

import java.util.ArrayList;
import java.util.List;

public class NotificationPage {
    public List<AppNotification> items = new ArrayList<>();
    public int unreadCount;
    public int total;
}
