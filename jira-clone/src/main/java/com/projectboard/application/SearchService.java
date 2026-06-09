package com.projectboard.application;

import com.projectboard.domain.model.ActivityEvent;
import com.projectboard.domain.ports.AccessControl;
import com.projectboard.infrastructure.persistence.ActivityRepository;
import com.projectboard.infrastructure.persistence.SearchRepository;

import java.util.List;

public class SearchService {

    private final SearchRepository searchRepo;
    private final ActivityRepository activityRepo;
    private final AccessControl access;

    public SearchService(SearchRepository searchRepo, ActivityRepository activityRepo, AccessControl access) {
        this.searchRepo = searchRepo;
        this.activityRepo = activityRepo;
        this.access = access;
    }

    public SearchRepository.SearchResult search(String q, String filter, String cursor, int limit) throws Exception {
        return searchRepo.search(q, filter, cursor, limit);
    }

    public List<ActivityEvent> activity(String projectId, String userId, String cursor,
                                         int limit, String eventType, String issueId) throws Exception {
        access.requireAccess(projectId, userId);
        return activityRepo.list(projectId, cursor, limit, eventType, issueId);
    }
}
