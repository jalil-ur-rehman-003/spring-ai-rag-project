package com.documind.auth.web;

import java.util.UUID;

public record RegisterOrganizationResponse(UUID organizationId, UUID adminUserId) {
}
