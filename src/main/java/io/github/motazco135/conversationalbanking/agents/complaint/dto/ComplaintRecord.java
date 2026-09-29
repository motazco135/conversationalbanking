package io.github.motazco135.conversationalbanking.agents.complaint.dto;

public record ComplaintRecord(
        String complaintId,
        String customerId,
        String category,
        String complaintDetails,
        String relatedReference,
        ComplaintStatus complaintStatus
) {
}
