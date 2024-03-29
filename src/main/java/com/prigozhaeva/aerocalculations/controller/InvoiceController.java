package com.prigozhaeva.aerocalculations.controller;

import com.lowagie.text.pdf.BaseFont;
import com.prigozhaeva.aerocalculations.dto.InvoiceDTO;
import com.prigozhaeva.aerocalculations.dto.InvoicePaymentTermsDTO;
import com.prigozhaeva.aerocalculations.entity.*;
import com.prigozhaeva.aerocalculations.service.*;
import com.prigozhaeva.aerocalculations.util.CityCodeMap;
import com.prigozhaeva.aerocalculations.util.MappingUtils;
import com.sun.mail.smtp.SMTPSendFailedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.mail.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.prigozhaeva.aerocalculations.constant.Constant.*;

@Controller
@RequestMapping(value = "/invoices")
public class InvoiceController {
    private InvoiceService invoiceService;
    private FlightService flightService;
    private ServiceService serviceService;
    private AirlineService airlineService;
    private UserService userService;
    private MappingUtils mappingUtils;

    public InvoiceController(InvoiceService invoiceService, FlightService flightService, ServiceService serviceService, MappingUtils mappingUtils, AirlineService airlineService, UserService userService) {
        this.invoiceService = invoiceService;
        this.flightService = flightService;
        this.serviceService = serviceService;
        this.mappingUtils = mappingUtils;
        this.airlineService = airlineService;
        this.userService = userService;
    }

    @GetMapping(value = "/index")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String invoices(Model model, @RequestParam(name = KEYWORD, defaultValue = "") String keyword) {
//        List<Invoice> invoices = invoiceService.fetchAll();  //delete
//        invoices.get(0).setFlight(flightService.findFlightById(2001399399L)); //delete
//        invoices.get(1).setFlight(flightService.findFlightById(2001406321L)); //delete
//        invoices.get(2).setFlight(flightService.findFlightById(2001406319L)); //delete
//        for (Invoice invoice : invoices) {
//            invoiceService.createOrUpdateInvoice(invoice);  //delete
//        }
        List<InvoiceDTO> invoiceList;
        if (keyword.isEmpty()) {
            invoiceList = new CopyOnWriteArrayList<>(invoiceService.fetchAllDto().stream()
                    .filter(invoiceDTO -> !invoiceDTO.getPaymentState().equals(PAID_STATUS))
                    .collect(Collectors.toList()));
        } else {
            invoiceList = new CopyOnWriteArrayList<>(invoiceService.findInvoicesDtoByInvoiceNumber(Integer.parseInt(keyword)).stream()
                    .filter(invoiceDTO -> !invoiceDTO.getPaymentState().equals(PAID_STATUS))
                    .collect(Collectors.toList()));
        }
        model.addAttribute(LIST_INVOICES, invoiceList);
        model.addAttribute(KEYWORD, keyword);
        List<Airline> airlines = airlineService.fetchAll();
        model.addAttribute(LIST_AIRLINES, airlines);
        return "invoice-views/invoices";
    }

    @PostMapping(value = "/changePaymentStatus")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant')")
    public String changePaymentStatus(int invoiceNumber, String paymentState) {
        invoiceService.changePaymentStatus(invoiceNumber, paymentState);
        return "redirect:/invoices/index";
    }

    @GetMapping(value = "/formCreate")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant')")
    public String formCreate(Model model, String flightNumberModal, String invoiceCreationDate) {
        if (flightNumberModal.isEmpty() || invoiceCreationDate.isEmpty()) {
            return "invoice-views/msgPage";
        }
        Flight flight = flightService.findFlightByFlightNumberAndDepDate(flightNumberModal, LocalDate.parse(invoiceCreationDate));
        if (flight == null) {
            return "invoice-views/msgPage";
        }
        List<ProvidedService> airportServicesCheck = filterByServiceType(flight.getProvidedServices(), AIRPORT_SERVICES);
        List<ProvidedService> groundHandlingServicesCheck = filterByServiceType(flight.getProvidedServices(), GROUND_HANDLING_SERVICES);
        List<ProvidedService> airportServices = filterAndMapServices(AIRPORT_SERVICES, airportServicesCheck);
        List<ProvidedService> groundHandlingServices = filterAndMapServices(GROUND_HANDLING_SERVICES, groundHandlingServicesCheck);
        Optional<Integer> number = invoiceService.fetchAll().stream()
                .map(Invoice::getInvoiceNumber)
                .max(Integer::compareTo);
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        model.addAttribute(FLIGHT, flight);
        model.addAttribute(AIRPORT_SERVICES_MODEL_CHECK, airportServicesCheck);
        model.addAttribute(GROUND_HANDLING_SERVICES_MODEL_CHECK, groundHandlingServicesCheck);
        model.addAttribute(AIRPORT_SERVICES_MODEL, airportServices);
        model.addAttribute(GROUND_HANDLING_SERVICES_MODEL, groundHandlingServices);
        model.addAttribute(INVOICE, Invoice.builder()
                .invoiceCreationDate(LocalDate.parse(currentDate))
                .invoiceNumber(number.orElse(0) + 1)
                .build()
        );
        return "invoice-views/formCreate";
    }

    private List<ProvidedService> filterByServiceType(List<ProvidedService> services, String serviceType) {
        return services.stream()
                .filter(providedService -> providedService.getService().getServiceType().equals(serviceType))
                .collect(Collectors.toList());
    }

    private List<ProvidedService> filterAndMapServices(String serviceType, List<ProvidedService> servicesToCheck) {
        return serviceService.fetchAll().stream()
                .filter(service -> service.getServiceType().equals(serviceType))
                .filter(service -> servicesToCheck.stream()
                        .noneMatch(providedService -> providedService.getService().getId().equals(service.getId())))
                .map(service -> ProvidedService.builder().service(service).build())
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/confirmForm")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String confirmForm(int invoiceNumber, Model model) {
        Invoice invoiceDB = invoiceService.findInvoiceByInvoiceNumber(invoiceNumber);
        InvoiceDTO invoiceDTO = mappingUtils.mapToInvoiceDTO(invoiceDB);
        model.addAttribute(INVOICE, invoiceDTO);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter formatterT = DateTimeFormatter.ofPattern("HH:mm");
        model.addAttribute(FORMATTED_DATE_INVOICE, invoiceDTO.getInvoiceCreationDate().format(formatter));
        model.addAttribute(FORMATTED_DATE_DEP_DATE, invoiceDTO.getFlight().getDepDate().format(formatter));
        model.addAttribute(FORMATTED_DATE_ARR_DATE, invoiceDTO.getFlight().getArrDate().format(formatter));
        model.addAttribute(FORMATTED_DATE_DEP_TIME, invoiceDTO.getFlight().getDepTime().format(formatterT));
        model.addAttribute(FORMATTED_DATE_ARR_TIME, invoiceDTO.getFlight().getArrTime().format(formatterT));
        model.addAttribute(DEP_CITY, CityCodeMap.getCityCodeMap().get(invoiceDTO.getFlight().getDepCity()));
        model.addAttribute(ARR_CITY, CityCodeMap.getCityCodeMap().get(invoiceDTO.getFlight().getArrCity()));
        model.addAttribute(CURRENCY_SYMBOL, Currency.getInstance(invoiceDTO.getCurrency()).getSymbol());
        return "invoice-views/confirmForm";
    }

    @GetMapping(value = "/saveToPdf")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String saveToPdf(int invoiceNumber) {
        Invoice invoiceDB = invoiceService.findInvoiceByInvoiceNumber(invoiceNumber);
        InvoiceDTO invoiceDTO = mappingUtils.mapToInvoiceDTO(invoiceDB);
        String templatePath = "D:/diploma/проект/aeroCalculations/src/main/resources/templates/invoice-views/invoicePdf.html";
        String htmlContent = readHtmlContent(templatePath);
        String filledHtmlContent = fillTemplateWithData(htmlContent, invoiceDTO);
        String pdfOutputPath = "D:/diploma/проект/pdf/" + invoiceNumber + ".pdf";
        convertHtmlToPdf(filledHtmlContent, pdfOutputPath);
        return "redirect:/invoices/index";
    }

    private static String readHtmlContent(String templatePath) {
        try {
            Path path = Paths.get(templatePath);
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String fillTemplateWithData(String htmlContent, InvoiceDTO invoiceDTO) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter formatterT = DateTimeFormatter.ofPattern("HH:mm");
        String formattedDateInvoice = invoiceDTO.getInvoiceCreationDate().format(formatter);
        String formattedDateDepDate = invoiceDTO.getFlight().getDepDate().format(formatter);
        String formattedDateArrDate = invoiceDTO.getFlight().getArrDate().format(formatter);
        String formattedDateDepTime = invoiceDTO.getFlight().getDepTime().format(formatterT);
        String formattedDateArrTime = invoiceDTO.getFlight().getArrTime().format(formatterT);
        Context context = new Context();
        context.setVariable(INVOICE, invoiceDTO);
        context.setVariable(FORMATTED_DATE_INVOICE, formattedDateInvoice);
        context.setVariable(FORMATTED_DATE_DEP_DATE, formattedDateDepDate);
        context.setVariable(FORMATTED_DATE_ARR_DATE, formattedDateArrDate);
        context.setVariable(FORMATTED_DATE_DEP_TIME, formattedDateDepTime);
        context.setVariable(FORMATTED_DATE_ARR_TIME, formattedDateArrTime);
        context.setVariable(DEP_CITY, CityCodeMap.getCityCodeMap().get(invoiceDTO.getFlight().getDepCity()));
        context.setVariable(ARR_CITY, CityCodeMap.getCityCodeMap().get(invoiceDTO.getFlight().getArrCity()));
        context.setVariable(CURRENCY_SYMBOL, Currency.getInstance(invoiceDTO.getCurrency()).getSymbol());
        TemplateEngine templateEngine = new TemplateEngine();
        return templateEngine.process(htmlContent, context);
    }

    public static void convertHtmlToPdf(String htmlContent, String pdfFilePath) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            String fontPath = "D:/diploma/проект/aeroCalculations/src/main/resources/fonts";
            String cyrillicFontPath = fontPath + "/arial.ttf";
            renderer.getFontResolver().addFont(cyrillicFontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            try (OutputStream os = new FileOutputStream(pdfFilePath)) {
                renderer.createPDF(os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PostMapping(value = "/filter")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String filter(Model model, Long airlineId, String paymentStatus, String date1, String date2) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAll().stream()
                .filter(invoice -> (airlineId == null || invoice.getFlight().getAircraft().getAirline().getId().equals(airlineId)) &&
                        (paymentStatus.isEmpty() || invoice.getPaymentState().equals(paymentStatus)) &&
                        (date1.isEmpty() || invoice.getInvoiceCreationDate().compareTo(LocalDate.parse(date1)) >= 0) &&
                        (date2.isEmpty() || invoice.getInvoiceCreationDate().compareTo(LocalDate.parse(date2)) <= 0))
                .map(mappingUtils::mapToInvoiceDTO)
                .collect(Collectors.toList());
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/invoices";
    }

    @PostMapping(value = "/filterArchive")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String filterArchive(Model model, Long airlineId, String paymentStatus, String date1, String date2) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAll().stream()
                .filter(invoice -> invoice.getPaymentState().equals(PAID_STATUS) &&
                        (airlineId == null || invoice.getFlight().getAircraft().getAirline().getId().equals(airlineId)) &&
                        (paymentStatus.isEmpty() || invoice.getPaymentState().equals(paymentStatus)) &&
                        (date1.isEmpty() || invoice.getInvoiceCreationDate().compareTo(LocalDate.parse(date1)) >= 0) &&
                        (date2.isEmpty() || invoice.getInvoiceCreationDate().compareTo(LocalDate.parse(date2)) <= 0))
                .map(mappingUtils::mapToInvoiceDTO)
                .collect(Collectors.toList());
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/archive";
    }

    @GetMapping(value = "/sortByInvoiceNumber")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String sortByInvoiceNumber(Model model) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAllDto();
        Collections.sort(invoiceDTOList, Comparator.comparing(InvoiceDTO::getInvoiceNumber));
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/invoices";
    }

    @GetMapping(value = "/sortByInvoiceCreationDate")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String sortByCreationDate(Model model) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAllDto();
        Collections.sort(invoiceDTOList, Comparator.comparing(InvoiceDTO::getInvoiceCreationDate));
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/invoices";
    }

    @GetMapping(value = "/sortByInvoiceNumberArchive")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String sortByInvoiceNumberArchive(Model model) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAllDto().stream()
                .filter(invoiceDTO -> invoiceDTO.getPaymentState().equals(PAID_STATUS))
                .collect(Collectors.toList());
        Collections.sort(invoiceDTOList, Comparator.comparing(InvoiceDTO::getInvoiceNumber));
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/archive";
    }

    @GetMapping(value = "/sortByInvoiceCreationDateArchive")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String sortByInvoiceCreationDateArchive(Model model) {
        List<InvoiceDTO> invoiceDTOList = invoiceService.fetchAllDto().stream()
                .filter(invoiceDTO -> invoiceDTO.getPaymentState().equals(PAID_STATUS))
                .collect(Collectors.toList());
        Collections.sort(invoiceDTOList, Comparator.comparing(InvoiceDTO::getInvoiceCreationDate));
        model.addAttribute(LIST_INVOICES, invoiceDTOList);
        model.addAttribute(LIST_AIRLINES, airlineService.fetchAll());
        return "invoice-views/archive";
    }

    @GetMapping(value = "/formUpdate")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant')")
    public String updateInvoice(Model model, int invoiceNumber) {
        InvoiceDTO dto = invoiceService.findInvoiceDtoByInvoiceNumber(invoiceNumber);
        List<ProvidedService> airportServices = filterAndMapServices(AIRPORT_SERVICES, dto.getAirportServices());
        List<ProvidedService> groundHandlingServices = filterAndMapServices(GROUND_HANDLING_SERVICES, dto.getGroundHandlingServices());
        model.addAttribute(INVOICE, dto);
        model.addAttribute(AIRPORT_SERVICES_MODEL, airportServices);
        model.addAttribute(GROUND_HANDLING_SERVICES_MODEL, groundHandlingServices);
        return "invoice-views/formUpdate";
    }

    @GetMapping(value = "/delete")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant')")
    public String deleteInvoice(Long invoiceId) {
        invoiceService.removeInvoice(invoiceId);
        return "redirect:/invoices/index";
    }

    @GetMapping(value = "/formCreateFile")
    @PreAuthorize("hasAnyAuthority('Admin', 'Finance department employee')")
    public String createFile(Integer invoiceNumber, Model model) {
        InvoicePaymentTermsDTO invoicePaymentTermsDTO = new InvoicePaymentTermsDTO();
        invoicePaymentTermsDTO.setInvoiceNumber(invoiceNumber);
        model.addAttribute(INVOICE_PAYMENT_TERMS, invoicePaymentTermsDTO);
        return "invoice-views/createFileWithTermsPayment";
    }

    @PostMapping(value = "/createFile")
    @PreAuthorize("hasAnyAuthority('Admin', 'Finance department employee')")
    public  String createFileWithPaymentTerms(InvoicePaymentTermsDTO invoicePaymentTermsDTO) {
        String templatePath = "D:/diploma/проект/aeroCalculations/src/main/resources/templates/invoice-views/termsOfPayment.html";
        String htmlContent = readHtmlContent(templatePath);
        String filledHtmlContent = fillTermsOfPaymentTemplateWithData(htmlContent, invoicePaymentTermsDTO);
        String pdfOutputPath = "D:/diploma/проект/pdf/payment_" + invoicePaymentTermsDTO.getInvoiceNumber() + ".pdf";
        convertHtmlToPdf(filledHtmlContent, pdfOutputPath);
        Invoice invoice = invoiceService.findInvoiceByInvoiceNumber(invoicePaymentTermsDTO.getInvoiceNumber());
        invoice.setDueDate(invoicePaymentTermsDTO.getDueDate());
        invoiceService.createOrUpdateInvoice(invoice);
        return "redirect:/invoices/index";
    }

    private String fillTermsOfPaymentTemplateWithData(String htmlContent, InvoicePaymentTermsDTO invoicePaymentTermsDTO) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String formattedDateInvoice = invoicePaymentTermsDTO.getDueDate().format(formatter);
        Context context = new Context();
        context.setVariable(FORMATTED_DATE_INVOICE, formattedDateInvoice);
        context.setVariable(CURRENCY_SYMBOL, Currency.getInstance(invoicePaymentTermsDTO.getCurrency()).getSymbol());
        context.setVariable(INVOICE_PAYMENT_TERMS, invoicePaymentTermsDTO);
        TemplateEngine templateEngine = new TemplateEngine();
        return templateEngine.process(htmlContent, context);
    }

    @GetMapping(value = "/formMoreDetails")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String formMoreDetails(int invoiceNumber) {
        return "redirect:/invoices/confirmForm?invoiceNumber=" + invoiceNumber;
    }

    @GetMapping(value = "/sendInvoiceForm")
    @PreAuthorize("hasAnyAuthority('Admin', 'Finance department employee')")
    public String sendInvoiceForm() {
        return "invoice-views/formForSignDoc";
    }

    @GetMapping(value = "/signDocuments")
    @PreAuthorize("hasAnyAuthority('Admin', 'Finance department employee')")
    public String signDocuments(String invoiceDoc, String paymentTermsDoc, Model model) {
        invoiceService.signDocument(invoiceDoc);
        invoiceService.signDocument(paymentTermsDoc);
        model.addAttribute(INVOICE_DOC, invoiceDoc);
        model.addAttribute(PAYMENT_TERMS_DOC, paymentTermsDoc);
        return "invoice-views/formForSendEmail";
    }

    @PostMapping(value = "/sendByEmail")
    @PreAuthorize("hasAnyAuthority('Admin', 'Finance department employee')")
    public String sendByEmail(String recipientEmail, String msg, String invoiceDoc, String paymentTermsDoc, Model model) {
        try {
            invoiceService.sendByEmail(recipientEmail, "Счет на оплату", msg, invoiceDoc, paymentTermsDoc);
            Invoice invoice = invoiceService.findInvoiceByInvoiceNumber(Integer.parseInt(invoiceDoc.replaceAll("\\.pdf$", "")));
            invoice.setPaymentState(SENT_STATUS);
            invoiceService.createOrUpdateInvoice(invoice);
        } catch (SMTPSendFailedException e) {
            model.addAttribute("errorMessage", "Пользователь с таким email не найден");
            model.addAttribute("email", recipientEmail);
            model.addAttribute("invoiceDoc", invoiceDoc);
            model.addAttribute("paymentTermsDoc", paymentTermsDoc);
            model.addAttribute("message", msg);
            return "invoice-views/formForSendEmail";
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        return "redirect:/invoices/index";
    }

    @GetMapping(value = "/archive")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String archive(Model model, @RequestParam(name = KEYWORD, defaultValue = "") String keyword) {
        List<InvoiceDTO> invoiceList;
        if (keyword.isEmpty()) {
            invoiceList = new CopyOnWriteArrayList<>(invoiceService.fetchAllDto().stream()
                    .filter(invoiceDTO -> invoiceDTO.getPaymentState().equals(PAID_STATUS))
                    .collect(Collectors.toList()));
        } else {
            invoiceList = new CopyOnWriteArrayList<>(invoiceService.findInvoicesDtoByInvoiceNumber(Integer.parseInt(keyword)).stream()
                    .filter(invoiceDTO -> invoiceDTO.getPaymentState().equals(PAID_STATUS))
                    .collect(Collectors.toList()));
        }
        model.addAttribute(LIST_INVOICES, invoiceList);
        model.addAttribute(KEYWORD, keyword);
        List<Airline> airlines = airlineService.fetchAll();
        model.addAttribute(LIST_AIRLINES, airlines);
        return "invoice-views/archive";
    }
}
