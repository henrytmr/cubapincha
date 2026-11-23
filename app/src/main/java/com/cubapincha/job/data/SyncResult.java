package com.cubapincha.job.data;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.Job;

import java.util.ArrayList;
import java.util.List;

/** Cambios del servidor desde `since` (POST sync). */
public class SyncResult {
    public String serverTime;
    public List<Job> jobs = new ArrayList<>();
    public List<Application> applications = new ArrayList<>();
    public List<AppNotification> notifications = new ArrayList<>();
    public boolean hasMore;
}
