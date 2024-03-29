//package com.prigozhaeva.aerocalculations.runner;
//
//import com.prigozhaeva.aerocalculations.entity.Employee;
//import com.prigozhaeva.aerocalculations.entity.Invoice;
//import com.prigozhaeva.aerocalculations.entity.User;
//import com.prigozhaeva.aerocalculations.service.EmployeeService;
//import com.prigozhaeva.aerocalculations.service.InvoiceService;
//import com.prigozhaeva.aerocalculations.service.RoleService;
//import com.prigozhaeva.aerocalculations.service.UserService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//
//
//@Component
//public class MyRunner implements CommandLineRunner {
//    @Autowired
//    private UserService userService;
//    @Autowired
//    private RoleService roleService;
//    @Autowired
//    private EmployeeService employeeService;
//    @Autowired
//    private InvoiceService invoiceService;
//
//    public static final String ADMIN = "Admin";
//    public static final String ACCOUNTANT = "Accountant";
//    public static final String FINANCE = "Finance department employee";
//    @Override
//    public void run(String... args) throws Exception {
//
//        roleService.createRole(ADMIN);
//        roleService.createRole(ACCOUNTANT);
//        roleService.createRole(FINANCE);
//
//        User user1 = userService.createUser("astapovich_ch@mail.ru", "AdminA1111", ADMIN);
//        User user2 = userService.createUser( "bondarenko_b97@mail.ru", "margoPR2003", ACCOUNTANT);
//        User user3 = userService.createUser( "gromov_v90@mail.ru", "olgGR2003", FINANCE);
//
//        Employee employee1 = employeeService.createEmployee(user1.getId(), "Астапович", "Александра", "Владимировна", "+375448965214", "cистемный администратор", "/images/employees/Astapovich Aleksandra.jpg");
//        Employee employee2 = employeeService.createEmployee(user2.getId(), "Бондаренко", "Маргарита", "Александровна", "+375293514265", "бухгалтер", "/images/employees/Bondarenko Margarita.jpg");
//        Employee employee3 = employeeService.createEmployee(user3.getId(), "Громов", "Олег", "Андреевич", "+375335742145", "сотрудник финансового отдела", "/images/employees/default.jpg");
//
//        Invoice invoice3 = invoiceService.createInvoice(74124, LocalDate.parse("2023-12-14"), "BYN", "Не оплачен", 2L, null);
//        Invoice invoice1 = invoiceService.createInvoice(74122, LocalDate.parse("2023-12-12"), "EUR", "Оплачен", 2L, null);
//        Invoice invoice2 = invoiceService.createInvoice(74123, LocalDate.parse("2023-12-13"), "USD", "Отправлен", 2L, null);
//    }
//}
