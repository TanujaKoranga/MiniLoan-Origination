package com.miniloan.service;

import com.miniloan.model.Customer;
import com.miniloan.model.LoanApplication;
import com.miniloan.utility.BusinessDateProvider;
import com.miniloan.utility.ConfigLoader;
import com.miniloan.utility.IndianCurrencyFormatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Writes output/application_form_<applicationNumber>.html when credit approval happens. */
public class ApplicationFormGenerator {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ConfigLoader config;
    private final BusinessDateProvider clock;

    public ApplicationFormGenerator(ConfigLoader config, BusinessDateProvider clock) {
        this.config = config;
        this.clock = clock;
    }

    public Path generate(LoanApplication a, Customer c) throws IOException {
        Path dir = config.outputDir();
        Files.createDirectories(dir);
        Path file = dir.resolve("application_form_" + a.getApplicationNumber() + ".html");
        Files.writeString(file, render(a, c), StandardCharsets.UTF_8);
        return file;
    }

    String render(LoanApplication a, Customer c) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>Application Form ").append(esc(a.getApplicationNumber()))
         .append("</title>\n<style>body{font-family:Arial,sans-serif;max-width:760px;margin:30px auto;color:#222}")
         .append("h1{border-bottom:3px solid #1a4f8b;padding-bottom:6px;color:#1a4f8b}h2{color:#1a4f8b;margin-top:26px}")
         .append("table{width:100%;border-collapse:collapse}td{padding:6px 8px;border-bottom:1px solid #ddd}")
         .append("td:first-child{width:40%;font-weight:bold;background:#f4f7fb}.foot{margin-top:30px;font-size:12px;color:#666}")
         .append("</style></head><body>\n");
        h.append("<h1>Loan Application Form</h1>\n");
        h.append("<p><b>Application No:</b> ").append(esc(a.getApplicationNumber())).append(" &nbsp; <b>Status:</b> ")
         .append(a.getStatus()).append("</p>\n");

        h.append("<h2>Applicant</h2><table>");
        row(h, "Customer ID", String.valueOf(c.customerId()));
        row(h, "Name", c.name());
        row(h, "Date of birth", String.valueOf(c.dateOfBirth()));
        row(h, "Mobile", c.mobile());
        row(h, "Address", c.address());
        row(h, "KYC details", c.kycDetails());
        if (a.getCoBorrowerName() != null) row(h, "Co-borrower", a.getCoBorrowerName());
        h.append("</table>\n");

        h.append("<h2>Loan Details</h2><table>");
        row(h, "Product", a.getProduct().getDisplayName() + " (" + a.getProduct() + ")");
        row(h, "Branch", a.getBranchCode() + " - " + a.getBranchName());
        row(h, "Requested / approved amount", IndianCurrencyFormatter.format(a.getRequestedAmount()));
        row(h, "Tenure", a.getTenureMonths() + " months");
        row(h, "Annual interest rate", a.getAnnualInterestRate() + "% p.a.");
        row(h, "Monthly net income", IndianCurrencyFormatter.format(a.getMonthlyIncome()));
        row(h, "Existing monthly obligations", IndianCurrencyFormatter.format(a.getExistingMonthlyObligations()));
        h.append("</table>\n");

        h.append("<h2>EMI &amp; FOIR Assessment</h2><table>");
        row(h, "Proposed EMI", IndianCurrencyFormatter.format(a.getProposedEmi()));
        row(h, "FOIR", a.getFoirPercentage() + "%");
        row(h, "Eligibility", Boolean.TRUE.equals(a.getEligible()) ? "ELIGIBLE" : "NOT ELIGIBLE");
        row(h, "Eligibility notes", a.getEligibilityReasons() == null ? "-" : a.getEligibilityReasons());
        h.append("</table>\n");

        h.append("<h2>Credit Approval</h2><table>");
        row(h, "Submitted at", a.getSubmittedAt() == null ? "-" : a.getSubmittedAt().format(TS));
        row(h, "Approved at", a.getDecidedAt() == null ? "-" : a.getDecidedAt().format(TS));
        row(h, "Reviewer", a.getReviewerCode() == null ? "-" : a.getReviewerCode());
        row(h, "Decision remarks", a.getDecisionReason() == null ? "-" : a.getDecisionReason());
        h.append("</table>\n");
        h.append("<p class=\"foot\">Generated on ").append(clock.now().format(TS)).append(" (business date ")
         .append(clock.today()).append(").</p>\n</body></html>\n");
        return h.toString();
    }

    private static void row(StringBuilder h, String k, String v) {
        h.append("<tr><td>").append(esc(k)).append("</td><td>").append(esc(v)).append("</td></tr>");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
