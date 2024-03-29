package com.prigozhaeva.aerocalculations.repository;

import com.prigozhaeva.aerocalculations.entity.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = {"file:src/test/resources/db/clear_all.sql","file:src/test/resources/db/insert_script.sql"})

class InvoiceRepositoryTest {
    @Autowired
    private  InvoiceRepository invoiceRepository;
    @Test
    void testFindInvoiceByInvoiceNumberContains() {
        int INVOICE_NUMBER = 1234;
        List<Invoice> invoices = invoiceRepository.findInvoicesByInvoiceNumber(INVOICE_NUMBER);
        int expectedValue = 1;
        assertEquals(expectedValue, invoices.size());
    }
}