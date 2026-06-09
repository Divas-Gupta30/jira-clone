package com.projectboard.domain.model;

import java.util.List;

public record BoardColumn(String status, List<Issue> issues) {}
