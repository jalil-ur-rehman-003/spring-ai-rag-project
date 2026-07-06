package com.documind.admin.application;

import com.documind.common.error.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Aggregate usage queries for the admin dashboard. Uses direct JDBC rather
 * than JPA entity graphs since these are simple count/aggregate reads
 * spanning multiple tables, not domain operations -- no entity mutation or
 * business logic is involved, just reporting.
 */
@Service
public class UsageAnalyticsService {

    private static final String DOCUMENT_COUNTS_SQL = """
            SELECT
                COUNT(*) AS total_documents,
                COUNT(*) FILTER (WHERE status = 'READY') AS ready_documents,
                COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_documents
            FROM document
            WHERE organization_id = ?
            """;

    private static final String CHAT_SESSION_COUNT_SQL = "SELECT COUNT(*) FROM chat_session WHERE organization_id = ?";

    private static final String CHAT_MESSAGE_COUNT_SQL = """
            SELECT COUNT(*) FROM chat_message
            WHERE session_id IN (SELECT id FROM chat_session WHERE organization_id = ?)
            """;

    private static final String ORGANIZATION_STORAGE_SQL =
            "SELECT storage_used_bytes, storage_quota_bytes FROM organization WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public UsageAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrganizationUsageSummary summarizeUsage(UUID organizationId) {
        long[] documentCounts = jdbcTemplate.queryForObject(DOCUMENT_COUNTS_SQL, (resultSet, rowNumber) -> new long[]{
                resultSet.getLong("total_documents"),
                resultSet.getLong("ready_documents"),
                resultSet.getLong("failed_documents")
        }, organizationId);

        long totalChatSessions = jdbcTemplate.queryForObject(CHAT_SESSION_COUNT_SQL, Long.class, organizationId);
        long totalChatMessages = jdbcTemplate.queryForObject(CHAT_MESSAGE_COUNT_SQL, Long.class, organizationId);

        long[] storage = jdbcTemplate.query(ORGANIZATION_STORAGE_SQL, resultSet -> {
            if (!resultSet.next()) {
                throw EntityNotFoundException.forEntity("Organization", organizationId);
            }
            return new long[]{resultSet.getLong("storage_used_bytes"), resultSet.getLong("storage_quota_bytes")};
        }, organizationId);

        return new OrganizationUsageSummary(
                documentCounts[0], documentCounts[1], documentCounts[2],
                totalChatSessions, totalChatMessages,
                storage[0], storage[1]
        );
    }
}
