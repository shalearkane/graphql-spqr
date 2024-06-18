package io.leangen.graphql.metadata.strategy.query;

import io.leangen.graphql.metadata.execution.Executable;
import io.leangen.graphql.metadata.execution.FixedMethodInvoker;
import io.leangen.graphql.metadata.execution.LambdaInvoker;
import io.leangen.graphql.metadata.execution.MethodInvoker;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

    @Override
    public Executable<Method> create(Supplier<Object> targetSupplier, Method resolverMethod, AnnotatedType enclosingType, Class<?> exposedType) {


        if (targetSupplier == null) {
            try {
                return new LambdaInvoker(resolverMethod, enclosingType);
            } catch (Exception e) {
                System.out.println(e);
                return new MethodInvoker(resolverMethod, enclosingType);
            }
        } else return new FixedMethodInvoker(targetSupplier, resolverMethod, enclosingType);
    }
}
