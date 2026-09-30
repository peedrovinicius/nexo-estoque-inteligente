package br.com.nexoestoque.controller;

import br.com.nexoestoque.dto.BatchQualityRequest;
import br.com.nexoestoque.dto.LotRecallRequest;
import br.com.nexoestoque.dto.RecallCloseRequest;
import br.com.nexoestoque.dto.ReceiptVarianceRequest;
import br.com.nexoestoque.model.QualityControl.*;
import br.com.nexoestoque.repository.QualityControlRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quality")
public class QualityControlController {
    private final QualityControlRepository repository;

    public QualityControlController(QualityControlRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/summary")
    public QualitySummary summary() throws SQLException {
        return repository.summary();
    }

    @GetMapping("/batches")
    public List<BatchQualityState> heldBatches() throws SQLException {
        return repository.heldBatches();
    }

    @PostMapping("/batches/{batchId}/quarantine")
    public BatchQualityState quarantine(
            @PathVariable long batchId,
            @Valid @RequestBody BatchQualityRequest request,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.quarantineBatch(batchId,request.reason(),actor(authentication)));
    }

    @PostMapping("/batches/{batchId}/release")
    public BatchQualityState release(
            @PathVariable long batchId,
            @Valid @RequestBody BatchQualityRequest request,
            Authentication authentication
    ) throws SQLException {
        requireAdmin(authentication);
        return translate(() -> repository.releaseBatch(batchId,request.reason(),actor(authentication)));
    }

    @GetMapping("/recalls")
    public List<LotRecall> recalls(@RequestParam(required = false) String status) throws SQLException {
        return translate(() -> repository.recalls(status));
    }

    @PostMapping("/recalls")
    @ResponseStatus(HttpStatus.CREATED)
    public LotRecall createRecall(
            @Valid @RequestBody LotRecallRequest request,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.createRecall(request,actor(authentication)));
    }

    @PostMapping("/recalls/{recallId}/close")
    public LotRecall closeRecall(
            @PathVariable long recallId,
            @Valid @RequestBody RecallCloseRequest request,
            Authentication authentication
    ) throws SQLException {
        requireAdmin(authentication);
        return translate(() -> repository.closeRecall(recallId,request.resolution(),actor(authentication)));
    }

    @GetMapping("/recalls/{recallId}/impact")
    public RecallImpact recallImpact(@PathVariable long recallId) throws SQLException {
        return translate(() -> repository.recallImpact(recallId));
    }

    @GetMapping("/receipt-variances")
    public List<ReceiptVariance> variances(
            @RequestParam(defaultValue = "200") int limit
    ) throws SQLException {
        return repository.variances(limit);
    }

    @PostMapping("/receipt-variances")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptVariance createVariance(
            @Valid @RequestBody ReceiptVarianceRequest request,
            Authentication authentication
    ) throws SQLException {
        return translate(() -> repository.createVariance(request,actor(authentication)));
    }

    private void requireAdmin(Authentication authentication){
        boolean admin=authentication!=null&&authentication.getAuthorities().stream()
                .anyMatch(authority->"ROLE_ADMIN".equals(authority.getAuthority()));
        if(!admin){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Apenas administradores podem liberar lotes ou encerrar recalls"
            );
        }
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
