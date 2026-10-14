package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final ProjetRepository projetRepository;
    private final AuthorizationService authorizationService;

    public HomeController(ProjetRepository projetRepository, AuthorizationService authorizationService) {
        this.projetRepository = projetRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> getHomeData(@RequestParam(required = false) Long userId) {
        long currentUserId = authorizationService.currentUser().getId();
        if (userId != null && userId != currentUserId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot read another user's home");
        }
        List<Projet> owned = projetRepository.findByOwnerId(currentUserId);
        List<Projet> memberOf = projetRepository.findByMembersId(currentUserId).stream()
                .filter(p -> p.getOwner() == null || p.getOwner().getId() != currentUserId)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("ownedProjects", owned);
        response.put("memberProjects", memberOf);
        return response;
    }
}
