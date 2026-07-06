package com.documind.admin.application;

/** Aggregate usage snapshot for one organization, surfaced on the admin dashboard. */
public record OrganizationUsageSummary(
        long totalDocuments,
        long readyDocuments,
        long failedDocuments,
        long totalChatSessions,
        long totalChatMessages,
        long storageUsedBytes,
        long storageQuotaBytes
) {
}
