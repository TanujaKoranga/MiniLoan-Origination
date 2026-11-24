package com.miniloan.model;

import java.util.List;

/** Everything needed to display one application. disbursement is null when not disbursed. */
public record ApplicationDetails(LoanApplication application, Customer customer,
                                 List<ApplicationHistory> history, Disbursement disbursement) { }
