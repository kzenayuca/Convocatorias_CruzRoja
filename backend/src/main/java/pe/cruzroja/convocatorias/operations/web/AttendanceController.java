package pe.cruzroja.convocatorias.operations.web;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.operations.application.AttendanceUseCases;
import pe.cruzroja.convocatorias.operations.application.AttendanceUseCases.*;
import pe.cruzroja.convocatorias.shared.web.PdfDocument;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {
    private final AttendanceUseCases useCases;
    public AttendanceController(AttendanceUseCases useCases) { this.useCases=useCases; }
    @GetMapping("/attendance") public List<Map<String,Object>> mine() { return useCases.mine(); }
    @GetMapping("/callouts/{id}/attendance") public List<Map<String,Object>> team(@PathVariable UUID id) { return useCases.team(id); }
    @PostMapping("/enrollments/{id}/mark") public void mark(@PathVariable UUID id,@RequestBody Mark r) { useCases.mark(id,r); }
    @PostMapping("/attendance/{id}/confirm") public void confirm(@PathVariable UUID id,@RequestBody Confirmation r) { useCases.confirm(id,r); }
    @PostMapping("/enrollments/{id}/correction") public void correction(@PathVariable UUID id,@RequestBody Reason r) { useCases.correction(id,r); }
    @GetMapping("/attendance-reviews/{id}") public Map<String,Object> reviewRequest(@PathVariable UUID id) { return useCases.reviewRequest(id); }
    @PostMapping("/attendance-reviews/{id}/resolve") public void rejectReview(@PathVariable UUID id,@RequestBody Resolution r) { useCases.rejectReview(id,r); }
    @PostMapping(value="/attendance/{id}/justifications",consumes="application/json") public void justify(@PathVariable UUID id,@RequestBody Reason r) { useCases.justify(id,r); }
    @PostMapping(value="/attendance/{id}/justifications",consumes="multipart/form-data") public void justifyFile(@PathVariable UUID id,@RequestParam String reason,@RequestParam(required=false) org.springframework.web.multipart.MultipartFile file) throws java.io.IOException { useCases.justify(id,new Reason(reason),JustificationsController.bytes(file)); }
    @GetMapping("/justifications") public List<Map<String,Object>> justifications() { return useCases.justifications(); }
    @PostMapping("/justifications/{id}/resolve") public void resolve(@PathVariable UUID id,@RequestBody Resolution r) { useCases.resolve(id,r); }
    @GetMapping("/attendance/hours") public Map<String,Object> hours(@RequestParam(required=false) String month) { return useCases.hours(month); }
    @GetMapping("/attendance/progress") public Object progress() { return useCases.progress(); }
    @GetMapping("/reports") public List<Map<String,Object>> reports(@RequestParam String month) { return useCases.report(month); }
    @GetMapping("/reports/status") public Map<String,Object> status(@RequestParam String month) { return useCases.reportStatus(month); }
    @PostMapping("/reports/approve") public void approve(@RequestParam String month) { useCases.approve(month); }
    @GetMapping("/reports/pdf") public org.springframework.http.ResponseEntity<byte[]> pdf(@RequestParam String month) throws java.io.IOException { return PdfDocument.download(useCases.reportPdf(month)); }
    @GetMapping("/reports/annual") public List<Map<String,Object>> annual(@RequestParam int year) { return useCases.annualReport(year); }
    @GetMapping("/reports/annual/pdf") public org.springframework.http.ResponseEntity<byte[]> annualPdf(@RequestParam int year) throws java.io.IOException { return PdfDocument.download(useCases.annualPdf(year)); }
    @GetMapping("/attendance/year") public Map<String,Object> personalYear(@RequestParam int year) { return useCases.personalYear(year); }
    @GetMapping("/attendance/year/pdf") public org.springframework.http.ResponseEntity<byte[]> personalYearPdf(@RequestParam int year) throws java.io.IOException { return PdfDocument.download(useCases.personalYearPdf(year)); }
    @GetMapping("/attendance/hours/pdf") public org.springframework.http.ResponseEntity<byte[]> personalMonthPdf(@RequestParam String month) throws java.io.IOException { return PdfDocument.download(useCases.personalMonthPdf(month)); }
}
