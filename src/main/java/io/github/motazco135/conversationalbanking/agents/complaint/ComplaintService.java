package io.github.motazco135.conversationalbanking.agents.complaint;

import io.github.motazco135.conversationalbanking.agents.complaint.dto.ComplaintRecord;
import io.github.motazco135.conversationalbanking.agents.complaint.dto.ComplaintStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ComplaintService {

    /**
     * Persist a new complaint for the customer. Structural validation (a non-blank description) is
     * performed upstream by the {@code createComplaint} workflow tool; this method assumes it has
     * already been given a usable description in the customer's own words and returns a record with
     * a freshly-minted reference id (mock persistence — no real core-banking integration yet).
     */
    public ComplaintRecord createComplaint(String customerId, String category, String description, String relatedReference) {
        log.info("Creating complaint for customer {} category {}", customerId, category);
        String complaintReferenceId = "COMP-" + ThreadLocalRandom.current().nextInt(10000, 100000);
        return new ComplaintRecord(complaintReferenceId, customerId, category, description, relatedReference, ComplaintStatus.PENDING);
    }

    public ComplaintRecord getComplaint(String customerId, String complaintId) {
        log.info("Getting complaint {} , for customer {}", complaintId, customerId);
        if (customerId == null || complaintId == null || complaintId.isBlank()) {
            throw new IllegalArgumentException("Customer Id or Complaint Id can not be null");
        }
        return new ComplaintRecord("COMP-1", customerId, "GENERAL", "Test Test Test", null, ComplaintStatus.IN_PROGRESS);
    }
}
