package com.example.library.drools;

import com.example.library.BorrowingContext;
import com.example.library.BorrowingDecision;
import com.example.library.BorrowingPolicy;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DroolsBorrowingPolicy implements BorrowingPolicy {
    private final KieBase rules;

    public DroolsBorrowingPolicy(Resource resource) throws IOException {
        // Load and compile the rule file once, when Spring creates this policy.
        String drl = resource.getContentAsString(StandardCharsets.UTF_8);
        rules = new KieHelper().addContent(drl, ResourceType.DRL).build(ExecutableModelProject.class);
    }

    @Override
    public BorrowingDecision evaluate(BorrowingContext context) {
        List<BorrowingDecision> decisions = new ArrayList<>();
        StatelessKieSession session = rules.newStatelessKieSession();
        session.setGlobal("decisions", decisions); // Where the matching rule writes its result.
        session.execute(context);                 // Insert the fact and run the rules.

        // The five rules are mutually exclusive: at most one matches this request.
        return decisions.isEmpty()
                ? BorrowingDecision.reject("Borrowing limit reached or no matching borrowing rule")
                : decisions.getFirst();
    }
}
