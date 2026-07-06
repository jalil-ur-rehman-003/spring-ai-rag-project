package com.documind.admin.web;

public record OrganizationUsageResponse(
        long totalDocuments,
        long readyDocuments,
        long failedDocuments,
        long totalChatSessions,
        long totalChatMessages,
        long storageUsedBytes,
        long storageQuotaBytes
) {
}
