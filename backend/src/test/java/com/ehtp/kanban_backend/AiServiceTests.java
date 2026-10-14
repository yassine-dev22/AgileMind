package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.WorkloadDTO;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.TaskRepository;
import com.ehtp.kanban_backend.service.AiService;
import com.ehtp.kanban_backend.service.GeminiClient;
import com.ehtp.kanban_backend.service.MetricsService;
import com.ehtp.kanban_backend.service.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiServiceTests {
    @Test
    void localReassignmentChoosesAnActiveTaskThatFitsTheTargetCapacity() {
        var metrics = mock(MetricsService.class);
        var repository = mock(TaskRepository.class);
        var gemini = mock(GeminiClient.class);
        var prompts = mock(PromptBuilder.class);
        var service = new AiService(metrics, repository, gemini, prompts, new ObjectMapper());
        when(gemini.requestJson(anyString())).thenReturn(Optional.empty());
        when(prompts.reassignmentPrompt(anyList(), anyList(), anyList())).thenReturn("prompt");
        User source = new User(); source.setId(1);
        Task large = task(source, 10, 1);
        Task small = task(source, 4, 2);
        when(repository.findBySprintSprintId(1L)).thenReturn(List.of(large, small));
        when(metrics.getBottlenecks(1L)).thenReturn(List.of());
        when(metrics.getWorkload(1L)).thenReturn(List.of(
                new WorkloadDTO(1L, "Overloaded", 14, 0, 14, 10, 1.4, true, true),
                new WorkloadDTO(2L, "Available", 5, 0, 5, 10, 0.5, false, false)));
        var suggestions = service.reassignmentSuggestions(1L).suggestedReassignments();
        assertEquals(1, suggestions.size());
        assertEquals(2L, suggestions.get(0).taskId());
        assertEquals(2L, suggestions.get(0).toUserId());
        when(metrics.getWorkload(1L)).thenReturn(List.of(
                new WorkloadDTO(1L, "Overloaded", 14, 0, 14, 10, 1.4, true, true),
                new WorkloadDTO(2L, "Available", 9, 0, 9, 10, 0.9, false, false)));
        assertTrue(service.reassignmentSuggestions(1L).suggestedReassignments().isEmpty());
    }

    private Task task(User source, int points, long id) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Task " + id);
        task.setAssignee(source);
        task.setEffortPoints(points);
        task.setStatus(Task.TaskStatus.IN_PROGRESS);
        return task;
    }
}
