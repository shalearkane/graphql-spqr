package io.leangen.graphql.metadata.execution;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Created by bojan.tomic on 7/20/16.
 */
public class FixedLambdaInvoker extends LambdaInvoker {

    final private Supplier<Object> targetSupplier;

    public FixedLambdaInvoker(final Supplier<Object> targetSupplier, final Method resolverMethod, final AnnotatedType enclosingType) throws Exception {
        super(resolverMethod, enclosingType);
        this.targetSupplier = targetSupplier;
        System.out.println("FixedLambdaInvoker is getting invoked for " + resolverMethod.getName());
    }

    @Override
    public Object execute(final Object target, final Object[] arguments){
        return this.lambdaGetter.apply(this.targetSupplier.get());
    }
}