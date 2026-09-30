package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.ActionAcknowledgementRequest;
import br.com.nexoestoque.dto.OperationalExceptionRequest;
import br.com.nexoestoque.dto.ReplenishmentPolicyRequest;
import br.com.nexoestoque.model.OperationalGovernance.*;
import br.com.nexoestoque.repository.OperationalGovernanceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/governance")
public class OperationalGovernanceController {
    private final OperationalGovernanceRepository repository;

    public OperationalGovernanceController(OperationalGovernanceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/policies")
    public List<ReplenishmentPolicy> policies() throws SQLException {
        return repository.policies();
    }

    @PutMapping("/policies/{productId}")
    public ReplenishmentPolicy savePolicy(
            @PathVariable long productId,
            @Valid @RequestBody ReplenishmentPolicyRequest request,
            Authentication authentication
    ) throws SQLException {
        requireAdmin(authentication);
        return translate(() -> repository.upsertPolicy(productId,request,actor(authentication)));
    }

    @GetMapping("/exceptions")
    public List<OperationalException> exceptions(
            @RequestParam(required = false) String status
    ) throws SQLException {
        return repository.exceptions(status);
    }

    @PostMapping("/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationalException createException(
            @Valid @RequestBody OperationalExceptionRequest request,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.createException(request,actor(authentication)));
    }

    @PostMapping("/exceptions/{id}/cancel")
    public OperationalException cancelException(
            @PathVariable long id,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.cancelException(id,actor(authentication)));
    }

    @GetMapping("/cycle-counts")
    public List<CycleCountSuggestion> cycleCounts(
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.cycleCounts(limit);
    }

    @GetMapping("/actions")
    public List<GovernedAction> actions(
            @RequestParam(defaultValue = "false") boolean includeResolved
    ) throws SQLException {
        return repository.governedActions(includeResolved);
    }

    @PostMapping("/actions/{key}/acknowledge")
    public GovernedAction acknowledge(
            @PathVariable String key,
            @Valid @RequestBody(required = false) ActionAcknowledgementRequest request,
            Authentication authentication
    ) throws SQLException {
        String note=request==null?null:request.note();
        return translate(() -> repository.acknowledgeAction(key,actor(authentication),note));
    }

    @GetMapping("/summary")
    public GovernanceSummary summary() throws SQLException {
        return repository.summary();
    }

    private void requireAdmin(Authentication authentication){
        boolean admin=authentication!=null&&authentication.getAuthorities().stream()
                .anyMatch(authority->"ROLE_ADMIN".equals(authority.getAuthority()));
        if(!admin) throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Apenas administradores podem alterar políticas de reposição"
        );
    }

    private String actor(Authentication authentication){
        return authentication==null?"system":authentication.getName();
    }

    private <T>T translate(SqlOperation<T> operation) throws SQLException {
        try{
            return operation.run();
        }catch(SQLException exception){
            if("45000".equals(exception.getSQLState())){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,exception.getMessage(),exception);
            }
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T>{T run() throws SQLException;}
}
