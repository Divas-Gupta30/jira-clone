package com.projectboard.domain.model;

import java.util.List;

public record BoardView(String projectId, List<BoardColumn> columns) {}
