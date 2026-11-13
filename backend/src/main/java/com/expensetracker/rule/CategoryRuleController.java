package com.expensetracker.rule;

import com.expensetracker.category.CategoryKind;
import com.expensetracker.rule.CategoryRuleDtos.ApplyResult;
import com.expensetracker.rule.CategoryRuleDtos.CategoryRuleDto;
import com.expensetracker.rule.CategoryRuleDtos.CategoryRuleRequest;
import com.expensetracker.rule.CategoryRuleDtos.Suggestion;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class CategoryRuleController {

    private final CategoryRuleService service;

    public CategoryRuleController(CategoryRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryRuleDto> list() {
        return service.findAll();
    }

    @GetMapping("/suggest")
    public Suggestion suggest(@RequestParam String description, @RequestParam(required = false) CategoryKind kind) {
        return service.suggest(description, kind)
                .map(rule -> new Suggestion(rule.getCategory().getId(), rule.getId()))
                .orElse(new Suggestion(null, null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryRuleDto create(@Valid @RequestBody CategoryRuleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CategoryRuleDto update(@PathVariable Long id, @Valid @RequestBody CategoryRuleRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/apply")
    public ApplyResult apply(@PathVariable Long id) {
        return new ApplyResult(service.apply(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
