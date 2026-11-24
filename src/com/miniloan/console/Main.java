package com.miniloan.console;

import com.miniloan.dao.*;
import com.miniloan.service.*;
import com.miniloan.utility.*;

/** Entry point: wires everything together, starts the schedulers, runs the menu. */
public class Main {
    public static void main(String[] args) {
        UtilityScheduler scheduler = null;
        try {
            ConfigLoader config = ConfigLoader.fromDefaultLocation();
            BusinessDateProvider clock = new BusinessDateProvider(config);
            TransactionManager txm = new TransactionManager(new DbConnection(config));

            // fail fast with a clear message if the DB is not reachable
            txm.read(c -> c.isValid(3));

            CustomerDao customerDao = new CustomerDao();
            LoanApplicationDao appDao = new LoanApplicationDao();
            BranchDao branchDao = new BranchDao();
            DisbursementDao disbursementDao = new DisbursementDao();
            ApplicationHistoryDao historyDao = new ApplicationHistoryDao();
            SystemAlertDao alertDao = new SystemAlertDao();
            UtilityRunDao runDao = new UtilityRunDao();
            CoreBankingDao coreDao = new CoreBankingDao();

            OverdueReviewUtility overdue = new OverdueReviewUtility(config, clock, txm, appDao, alertDao, runDao, new CsvExporter());
            CoreDataPushUtility corePush = new CoreDataPushUtility(clock, txm, appDao, customerDao, disbursementDao, coreDao, historyDao, runDao);

            ValidationService validation = new ValidationService(config);
            EmiFoirCalculator calculator = new EmiFoirCalculator(config);
            ApplicationStateMachine stateMachine = new ApplicationStateMachine(historyDao, clock);
            ApplicationFormGenerator formGenerator = new ApplicationFormGenerator(config, clock);

            CustomerService customers = new CustomerService(txm, customerDao, appDao, validation, clock);
            ApplicationService applications = new ApplicationService(config, clock, txm, appDao, customerDao, branchDao,
                    disbursementDao, historyDao, validation, calculator, stateMachine, formGenerator);
            ReportService reports = new ReportService(config, txm, new ReportDao());
            MonitorService monitor = new MonitorService(txm, runDao, alertDao, coreDao);
            RollbackTestService rollbackTest = new RollbackTestService(clock, txm, customers, applications, corePush,
                    appDao, disbursementDao, coreDao, historyDao);

            scheduler = new UtilityScheduler(config, overdue, corePush);
            scheduler.start();

            System.out.println("Mini Loan Origination - connected to database. Business date: " + clock.today());
            new MenuController(new ConsoleIO(), config, clock, customers, applications, reports, monitor,
                    rollbackTest, overdue, corePush).run();
            System.out.println("Goodbye.");
        } catch (InputClosedException e) {
            System.out.println("\nInput closed. Exiting.");
        } catch (RuntimeException e) {
            System.err.println("\nStartup/fatal error: " + e.getMessage());
            Throwable cause = e.getCause();
            if (cause != null) System.err.println("Cause: " + cause);
            System.err.println("Check config.properties (db.url / db.user / db.password), that MySQL is running, and that schema.sql was loaded.");
            System.exit(1);
        } finally {
            if (scheduler != null) scheduler.stop();
        }
    }
}
