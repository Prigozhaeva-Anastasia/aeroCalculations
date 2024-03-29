package com.prigozhaeva.aerocalculations.controller;

import com.prigozhaeva.aerocalculations.entity.RushHour;
import com.prigozhaeva.aerocalculations.service.ReportService;
import com.prigozhaeva.aerocalculations.service.RushHourService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.prigozhaeva.aerocalculations.constant.Constant.*;
import static com.prigozhaeva.aerocalculations.constant.Constant.MAX_AVERAGE_FLIGHTS;

@Controller
@RequestMapping(value = "/rushHours")
public class RushHourController {
    private RushHourService rushHourService;
    private ReportService reportService;

    public RushHourController(RushHourService rushHourService, ReportService reportService) {
        this.rushHourService = rushHourService;
        this.reportService = reportService;
    }

    @GetMapping(value = "/index")
    @PreAuthorize("hasAnyAuthority('Admin', 'Accountant', 'Finance department employee')")
    public String rushHours(String weekDay, Model model) {
        List<RushHour> rushHours = rushHourService.findRushHoursByWeekDay(Integer.parseInt(weekDay));
        Collections.sort(rushHours, Comparator.comparing(RushHour::getFromTime));
//        Map<Integer, Long> flightOnWeekDay = reportService.flightDynamicsOnWeekDay(LocalDate.now().getYear(), LocalDate.now().getMonthValue() - 1, Integer.parseInt(weekDay)); это должно быть вместо следующей строки
        Map<Integer, Long> flightOnWeekDay = reportService.flightDynamicsOnWeekDay(2023, 10, Integer.parseInt(weekDay));
        Long maxAverageFlights = findMaxValue(flightOnWeekDay);
        if (flightOnWeekDay.size() != 0) {
            model.addAttribute(MAX_AVERAGE_FLIGHTS, maxAverageFlights.intValue() + 1);
        }
        model.addAttribute(AVERAGE_FLIGHTS_PER_HOUR, flightOnWeekDay);
        model.addAttribute(LIST_RUSH_HOURS, rushHours);
        model.addAttribute(WEEK_DAY, weekDay);
        model.addAttribute(RUSH_HOUR, new RushHour());
        return "rushHour-views/rushHours";
    }

    private static Long findMaxValue(Map<Integer, Long> averageFlightsPerHour) {
        return averageFlightsPerHour.values().stream()
                .max(Long::compare)
                .orElse(null);
    }

    @PostMapping(value = "/delete")
    @PreAuthorize("hasAuthority('Admin')")
    public String deleteInvoice(Long id, int weekDay) {
        rushHourService.removeRushHour(id);
        return "redirect:/rushHours/index?weekDay=" + weekDay;
    }

    @PostMapping(value = "/update")
    @PreAuthorize("hasAuthority('Admin')")
    public String update(RushHour rushHour) {
        rushHourService.createOrUpdateRushHour(rushHour);
        return "redirect:/rushHours/index?weekDay=" + rushHour.getWeekDay();
    }

    @PostMapping(value = "/create")
    @PreAuthorize("hasAuthority('Admin')")
    public String create(RushHour rushHour) {
        rushHourService.createOrUpdateRushHour(rushHour);
        return "redirect:/rushHours/index?weekDay=" + rushHour.getWeekDay();
    }
}
