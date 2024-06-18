package io.leangen.graphql.metadata.execution;

import io.leangen.graphql.util.ClassUtils;

import java.lang.invoke.*;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by bojan.tomic on 7/20/16.
 */
public class LambdaInvoker extends Executable<Method> {
    private final AnnotatedType enclosingType;
    private final AnnotatedType returnType;
    final Function<Object, Object> lambdaGetter;

    private static final Parameter[] NO_PARAMETERS = {};
    private static final AnnotatedType[] NO_ANNOTATED_TYPES = {};


    public static Optional<Function<Object, Object>> createGetter(final Method candidateMethod) throws Exception {
        if (candidateMethod != null) {
            if (candidateMethod.getParameterCount() > 0) {
                throw new Exception(candidateMethod.getName() + " cannot be called using LambdaInvoker");
            }

            try {
                Function<Object, Object> getterFunction = mkCallFunction(candidateMethod, candidateMethod.getDeclaringClass(), candidateMethod.getName(), candidateMethod.getReturnType());
                return Optional.of(getterFunction);
            } catch (Throwable e) {
                System.out.println(e);
                //
                // if we cant make a dynamic lambda here, then we give up and let the old property fetching code do its thing
                // this can happen on runtimes such as GraalVM native where LambdaMetafactory is not supported
                // and will throw something like :
                //
                //    com.oracle.svm.core.jdk.UnsupportedFeatureError: Defining hidden classes at runtime is not supported.
                //        at org.graalvm.nativeimage.builder/com.oracle.svm.core.util.VMError.unsupportedFeature(VMError.java:89)
            }
        }
        return Optional.empty();
    }

    static Function<Object, Object> mkCallFunction(final Method m, final Class<?> targetClass, final String targetMethod, final Class<?> targetMethodReturnType) throws Throwable {
        m.setAccessible(true);
        MethodHandles.Lookup lookupMe = MethodHandles.lookup();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), lookupMe);
        MethodHandle virtualMethodHandle = lookup.unreflect(m);
        CallSite site = LambdaMetafactory.metafactory(lookup, "apply", MethodType.methodType(Function.class), MethodType.methodType(Object.class, Object.class), virtualMethodHandle, MethodType.methodType(m.getReturnType(), m.getDeclaringClass()));
        @SuppressWarnings("unchecked") Function<Object, Object> getterFunction = (Function<Object, Object>) site.getTarget().invokeExact();
        return getterFunction;
    }

    public LambdaInvoker(final Method resolverMethod, final AnnotatedType enclosingType) throws Exception {
        this.delegate = resolverMethod;
        this.enclosingType = enclosingType;
        this.returnType = resolveReturnType(enclosingType);
        final Optional<Function<Object, Object>> lg = this.createGetter(resolverMethod);
        if (lg.isEmpty()) {
            throw new Exception("Cannot create a lambda getter for " + resolverMethod.getName());

        }

        this.lambdaGetter = lg.get();
        System.out.println("LambdaInvoker is getting invoked for " + resolverMethod.getName());
    }

    @Override
    public Object execute(final Object target, final Object[] args) {
        return lambdaGetter.apply(target);
    }

    @Override
    final public AnnotatedType getReturnType() {
        return returnType;
    }


    final private AnnotatedType resolveReturnType(final AnnotatedType enclosingType) {
        return ClassUtils.getReturnType(delegate, enclosingType);
    }

    /**
     * {@inheritDoc}
     *
     * @see java.lang.reflect.Executable#getParameterCount
     */
    @Override
    final public int getParameterCount() {
        return 0;
    }

    @Override
    final public AnnotatedType[] getAnnotatedParameterTypes() {
        return NO_ANNOTATED_TYPES;
    }

    @Override
    final public Parameter[] getParameters() {
        return NO_PARAMETERS;
    }
}
