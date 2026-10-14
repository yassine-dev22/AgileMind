package com.ehtp.kanban_backend.service.factory;

import com.ehtp.kanban_backend.dto.WorkloadDTO;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.service.MetricFormulaCalculator;
import java.util.*;

public class WorkloadCalculator implements MetricCalculator {
    @Override
    public List<WorkloadDTO> calculate(Sprint sprint, List<Task> tasks, MetricFormulaCalculator formulaCalculator) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (sprint.getProject() != null && sprint.getProject().getMembers() != null) {
            for (User member : sprint.getProject().getMembers()) {
                if (member.getRole() != User.Role.VIEWER) users.put(member.getId(), member);
            }
        }
        for (Task task : tasks) {
            if (task.getAssignee() != null && task.getAssignee().getRole() != User.Role.VIEWER) {
                users.put(task.getAssignee().getId(), task.getAssignee());
            }
        }

        List<WorkloadDTO> workloads = new ArrayList<>();
        Map<Long, int[]> totals = new HashMap<>();
        for (Task task : tasks) {
            if (task.getAssignee() == null) continue;
            int[] counts = totals.computeIfAbsent(task.getAssignee().getId(), ignored -> new int[2]);
            int points = task.getEffortPoints() == null ? 0 : task.getEffortPoints();
            counts[0] += points;
            if (task.getStatus() == Task.TaskStatus.DONE) counts[1] += points;
        }
        for (User user : users.values()) {
            int[] counts = totals.getOrDefault(user.getId(), new int[2]);
            int assigned = counts[0];
            int completed = counts[1];
            int remaining = assigned - completed;
            int capacity = user.getCapacityPoints() == null ? 0 : user.getCapacityPoints();
            double loadRatio = capacity <= 0 ? (assigned > 0 ? 999.0 : 0.0) : (double) assigned / capacity;
            
            double roundedLoadRatio = Math.round(loadRatio * 100.0) / 100.0;
            
            workloads.add(new WorkloadDTO(
                    user.getId(),
                    user.getFullName(),
                    assigned,
                    completed,
                    remaining,
                    capacity,
                    roundedLoadRatio,
                    capacity >= 0 && assigned > capacity,
                    loadRatio > 1.2
            ));
        }
        workloads.sort(Comparator.comparing(WorkloadDTO::fullName));
        return workloads;
    }
}
