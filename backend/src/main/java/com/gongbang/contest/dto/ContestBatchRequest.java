package com.gongbang.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ContestBatchRequest(@NotEmpty @Valid List<ContestBatchItem> contests) {}
