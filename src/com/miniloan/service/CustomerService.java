package com.miniloan.service;

import com.miniloan.dao.CustomerDao;
import com.miniloan.dao.LoanApplicationDao;
import com.miniloan.dao.TransactionManager;
import com.miniloan.model.*;
import com.miniloan.utility.BusinessDateProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class CustomerService {
    private final TransactionManager txm;
    private final CustomerDao customerDao;
    private final LoanApplicationDao appDao;
    private final ValidationService validation;
    private final BusinessDateProvider clock;

    public CustomerService(TransactionManager txm, CustomerDao customerDao, LoanApplicationDao appDao,
                           ValidationService validation, BusinessDateProvider clock) {
        this.txm = txm;
        this.customerDao = customerDao;
        this.appDao = appDao;
        this.validation = validation;
        this.clock = clock;
    }

    public Customer create(String name, LocalDate dob, String mobile, String address, String kyc) {
        validation.validateCustomerInput(name, dob, mobile, address, kyc, clock.today());
        String m = mobile.trim();
        String addr = (address == null || address.isBlank()) ? "ADDRESS NOT PROVIDED" : address.trim();
        String k = (kyc == null || kyc.isBlank()) ? "KYC PENDING" : kyc.trim();
        return txm.inTransaction(c -> {
            Optional<Customer> existing = customerDao.findByMobile(c, m);
            if (existing.isPresent()) throw new BusinessException(duplicateMessage(c, existing.get()));
            return customerDao.insert(c, new Customer(null, name.trim(), dob, m, addr, k), clock.now());
        });
    }

    private String duplicateMessage(Connection c, Customer existing) throws SQLException {
        StringBuilder sb = new StringBuilder("Mobile " + existing.mobile() + " is already registered to customer #"
                + existing.customerId() + " (" + existing.name() + "). A mobile number can belong to only one customer.");
        List<LoanApplication> active = appDao.findByCustomer(c, existing.customerId()).stream()
                .filter(a -> a.getStatus().isActive()).toList();
        if (!active.isEmpty()) {
            sb.append("\n   Active application(s) already open for this customer:");
            for (LoanApplication a : active)
                sb.append("\n   - ").append(a.getApplicationNumber()).append(" (").append(a.getProduct()).append(", ").append(a.getStatus()).append(")");
            sb.append("\n   A second active application for the same product is not allowed.");
        }
        return sb.toString();
    }

    public Customer require(long id) {
        return txm.read(c -> customerDao.findById(c, id)).orElseThrow(() -> new BusinessException("Customer " + id + " not found."));
    }

    public List<Customer> search(String term) {
        if (term == null || term.isBlank()) throw new BusinessException("Enter a customer ID, name or mobile number to search.");
        return txm.read(c -> customerDao.search(c, term));
    }
}
