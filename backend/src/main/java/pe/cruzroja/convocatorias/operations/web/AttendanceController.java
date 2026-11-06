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
    @PostMapping("/attendance/{id}/justifications") public void justify(@PathVariable UUID id,@RequestBody Reason r) { useCases.justify(id,r); }
    @GetMapping("/justifications") public List<Map<String,Object>> justifications() { return useCases.justifications(); }
    @PostMapping("/justifications/{id}/resolve") public void resolve(@PathVariable UUID id,@RequestBody Resolution r) { useCases.resolve(id,r); }
    @GetMapping("/attendance/hours") public Map<String,Object> hours(@RequestParam(required=false) String month) { return useCases.hours(month); }
    @GetMapping("/attendance/progress") public Object progress() { return useCases.progress(); }
    @GetMapping("/reports") public List<Map<String,Object>> reports(@RequestParam String month) { return useCases.report(month); }
    @GetMapping("/reports/status") public Map<String,Object> status(@RequestParam String month) { return useCases.reportStatus(month); }
    @PostMapping("/reports/approve") public void approve(@RequestParam String month) { useCases.approve(month); }
    @GetMapping("/reports/pdf") public org.springframework.http.ResponseEntity<byte[]> pdf(@RequestParam String month) throws java.io.IOException { return PdfDocument.download(useCases.reportPdf(month)); }
}
