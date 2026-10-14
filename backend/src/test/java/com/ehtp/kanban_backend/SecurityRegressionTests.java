package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.model.AiInsight;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.AiInsightRepository;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.SprintRepository;
import com.ehtp.kanban_backend.repository.UserRepository;
import com.ehtp.kanban_backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRegressionTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ProjetRepository projects;
    @Autowired SprintRepository sprints;
    @Autowired AiInsightRepository insights;
    @Autowired JwtService jwt;
    @Value("${app.jwt.secret}") String signingSecret;
    @Value("${app.ai.callback-secret}") String callbackSecret;
    private User owner;
    private User outsider;
    private User viewer;
    private Projet project;
    private Sprint sprint;

    @BeforeEach
    void seedProject() {
        owner = users.save(new User("security-owner@example.com", "Owner", "unused", User.Role.OWNER, 40));
        outsider = users.save(new User("security-outsider@example.com", "Outsider", "unused", User.Role.MEMBER, 40));
        viewer = users.save(new User("security-viewer@example.com", "Viewer", "unused", User.Role.VIEWER, 40));
        project = new Projet();
        project.setName("Private project");
        project.setDescription("Private data");
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setStatus(Projet.ProjectStatus.PLANNED);
        project.setOwner(owner);
        project.setMembers(new java.util.ArrayList<>(List.of(owner, viewer)));
        project = projects.save(project);
        sprint = new Sprint();
        sprint.setObjective("Private sprint");
        sprint.setStartDate(LocalDate.of(2026, 2, 1));
        sprint.setEndDate(LocalDate.of(2026, 2, 15));
        sprint.setPointsPlanned(10);
        sprint.setProject(project);
        sprint = sprints.saveAndFlush(sprint);
    }

    @Test
    void malformedExpiredAndNonExpiringTokensAreUnauthorized() throws Exception {
        for (String token : List.of("malformed.token", signedToken(Date.from(Instant.now().minusSeconds(60))), signedToken(null))) {
            mvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void callbackRequiresIndependentSecretEvenForAuthenticatedUsers() throws Exception {
        String payload = mapper.writeValueAsString(Map.of("sprintId", sprint.getSprintId(), "predictiveRisk", "Risk"));
        mvc.perform(post("/api/ai/callback").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/callback").header("Authorization", bearer(owner))
                        .header("X-AI-Callback-Token", "invalid").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        assertEquals(0, insights.findBySprintSprintId(sprint.getSprintId()).size());
        mvc.perform(post("/api/ai/callback").header("X-AI-Callback-Token", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        assertEquals(1, insights.findBySprintSprintId(sprint.getSprintId()).size());
    }

    @Test
    void invalidCallbackDoesNotEraseExistingInsights() throws Exception {
        AiInsight existing = new AiInsight();
        existing.setSprint(sprint);
        existing.setProject(project);
        existing.setExplanation("Keep this insight");
        existing.setInsightType(AiInsight.InsightType.RISK_ALERT);
        insights.saveAndFlush(existing);
        for (Map<String, Object> payload : List.<Map<String, Object>>of(
                Map.of("sprintId", sprint.getSprintId()),
                Map.of("sprintId", sprint.getSprintId(), "predictiveRisk", "x".repeat(4001)))) {
            mvc.perform(post("/api/ai/callback").header("X-AI-Callback-Token", callbackSecret)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
            assertEquals("Keep this insight", insights.findBySprintSprintId(sprint.getSprintId()).get(0).getExplanation());
        }
    }

    @Test
    void staleFutureAndUnversionedCallbacksCannotReplaceCurrentInsights() throws Exception {
        sprint.setAnalysisRevision(2);
        sprints.saveAndFlush(sprint);
        mvc.perform(post("/api/ai/callback").header("X-AI-Callback-Token", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                                Map.of("sprintId", sprint.getSprintId(), "analysisRevision", 2, "predictiveRisk", "Current risk"))))
                .andExpect(status().isOk());
        for (int revision : List.of(1, 3)) {
            mvc.perform(post("/api/ai/callback").header("X-AI-Callback-Token", callbackSecret)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                                    Map.of("sprintId", sprint.getSprintId(), "analysisRevision", revision, "predictiveRisk", "Outdated risk"))))
                    .andExpect(status().isConflict());
            assertEquals("Current risk", insights.findBySprintSprintId(sprint.getSprintId()).get(0).getExplanation());
        }
        mvc.perform(post("/api/ai/callback").header("X-AI-Callback-Token", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                                Map.of("sprintId", sprint.getSprintId(), "predictiveRisk", "Unversioned risk"))))
                .andExpect(status().isBadRequest());
        assertEquals("Current risk", insights.findBySprintSprintId(sprint.getSprintId()).get(0).getExplanation());
    }

    @Test
    void sprintInsightReadsRequireProjectMembership() throws Exception {
        mvc.perform(get("/api/ai/sprint/{id}", sprint.getSprintId()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/sprint/{id}", sprint.getSprintId()).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void viewersCannotUseMemberWritePermission() throws Exception {
        mvc.perform(put("/api/projects/{id}", project.getId()).header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Unauthorized change","description":"x","startDate":"2026-01-01","endDate":"2026-12-31"}
                                """))
                .andExpect(status().isForbidden());
        assertEquals("Private project", projects.findById(project.getId()).orElseThrow().getName());
    }

    @Test
    void viewerRoleCapsProjectOwnerPermissions() throws Exception {
        owner.setRole(User.Role.VIEWER);
        users.saveAndFlush(owner);
        mvc.perform(delete("/api/projects/{id}", project.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/{id}", project.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void homeRejectsForeignUserIdAndSupportsCurrentPrincipal() throws Exception {
        mvc.perform(get("/api/home").param("userId", Long.toString(owner.getId())).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/home").header("Authorization", bearer(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedProjects").isEmpty());
    }

    @Test
    void invalidJsonAndRegistrationPayloadsReturnBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"password\",\"capacityPoints\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "long-password@example.com", "password", "é".repeat(40)))))
                .andExpect(status().isBadRequest());
    }

    private String bearer(User user) { return "Bearer " + jwt.generateToken(user); }

    private String signedToken(Date expiration) {
        return Jwts.builder().subject(owner.getEmail()).expiration(expiration)
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
