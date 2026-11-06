package pe.cruzroja.convocatorias.membership.web;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.membership.application.AccountsUseCases;
import pe.cruzroja.convocatorias.membership.application.AccountsUseCases.*;

@RestController
@RequestMapping("/api/v1")
public class AccountsController {
    private final AccountsUseCases useCases;
    public AccountsController(AccountsUseCases useCases) { this.useCases=useCases; }
    @PostMapping("/auth/register") public Map<String,String> register(@RequestBody Registration r) { return useCases.register(r); }
    @GetMapping("/profile") public Map<String,Object> profile() { return useCases.profile(); }
    @PutMapping("/profile/phone") public void phone(@RequestBody Phone r) { useCases.phone(r); }
    @PostMapping("/profile/changes") public void change(@RequestBody Profile r) { useCases.change(r); }
    @GetMapping("/profile/changes") public List<Map<String,Object>> profileRequests() { return useCases.profileRequests(); }
    @GetMapping("/profile-requests/{id}") public Map<String,Object> profileRequest(@PathVariable UUID id) { return useCases.profileRequest(id); }
    @GetMapping("/users") public List<Map<String,Object>> users() { return useCases.users(); }
    @GetMapping("/users/{id}/graduation-progress") public pe.cruzroja.convocatorias.shared.application.GraduationEligibility.Assessment graduation(@PathVariable UUID id) { return useCases.graduationProgress(id); }
    @GetMapping("/users/changes") public List<Map<String,Object>> changes() { return useCases.changes(); }
    @PostMapping("/users/{id}/changes") public void resolveProfile(@PathVariable UUID id,@RequestBody Decision r) { useCases.resolveProfile(id,r); }
    @PutMapping("/users/{id}") public void review(@PathVariable UUID id,@RequestBody Review r) { useCases.review(id,r); }
}
