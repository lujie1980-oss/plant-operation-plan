package com.plantops.api.dto;

public record PipelineRunLogLineDto(String timestamp, String level, String message) {
}
