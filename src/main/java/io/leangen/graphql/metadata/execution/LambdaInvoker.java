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
    private final Function<Object, Object> lambdaGetter;

    public static Optional<Function<Object, Object>> createGetter(Method candidateMethod) throws Exception {
        if (candidateMethod != null) {
            if (candidateMethod.getParameterCount() > 0)  {
                throw new Exception("more than one arg or zero args");
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

    static Function<Object, Object> mkCallFunction(Method m, Class<?> targetClass, String targetMethod, Class<?> targetMethodReturnType) throws Throwable {
        m.setAccessible(true);
        MethodHandles.Lookup lookupMe = MethodHandles.lookup();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), lookupMe);
        MethodHandle virtualMethodHandle = lookup.unreflect(m);
//        MethodHandle virtualMethodHandle = lookup.findVirtual(m.getDeclaringClass(), m.getName(), MethodType.methodType(m.getReturnType()));
        CallSite site = LambdaMetafactory.metafactory(lookup,
                "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class),
                virtualMethodHandle,
                MethodType.methodType(m.getReturnType(), m.getDeclaringClass()));
        @SuppressWarnings("unchecked")
        Function<Object, Object> getterFunction = (Function<Object, Object>) site.getTarget().invokeExact();
        return getterFunction;
    }

    private static MethodHandles.Lookup getLookup(Class<?> targetClass) {
        MethodHandles.Lookup lookupMe = MethodHandles.lookup();
        //
        // This is a Java 9+ approach to method look up allowing private access
        //
        try {
            return MethodHandles.privateLookupIn(targetClass, lookupMe);
        } catch (IllegalAccessException e) {
            return lookupMe;
        }
    }


    public LambdaInvoker(Method resolverMethod, AnnotatedType enclosingType) throws Exception {
        this.delegate = resolverMethod;
        this.enclosingType = enclosingType;
        this.returnType = resolveReturnType(enclosingType);
        final Optional<Function<Object, Object>> lg = this.createGetter(resolverMethod);
        if (lg.isPresent()) {
            this.lambdaGetter = lg.get();
        } else {
            throw new Exception("Cannot create a lambda getter for " + resolverMethod.getName());
        }
    }

    @Override
    public Object execute(Object target, Object[] args) throws InvocationTargetException, IllegalAccessException {
        if (target == null) {
            System.out.println("Heading for disaster");
        }
        if (args.length == 0) {
            System.out.println("Args length is zero zero zero");
            System.out.println(target);
            System.out.println(this.delegate.getName());
            try {
                return lambdaGetter.apply(target);
            } catch (Exception e) {
                System.out.println(this.delegate.getDeclaringClass() + " funny ");
                for (java.lang.reflect.Parameter parameter : this.delegate.getParameters()) {
                    System.out.print(parameter);
                }
                System.out.println(e);
                try {
                    return this.delegate.invoke(target, args);
                } catch (Exception ex) {
                    System.out.println("Invoke failed tooo");
                    System.out.println(this.delegate);
                    System.out.println(target);
                    System.out.println(args);
                }
            }

            return null;
        }

        System.out.println("More than one arg oh shit");
        return this.delegate.invoke(target, args);
    }

    @Override
    public AnnotatedType getReturnType() {
        return returnType;
    }


    private AnnotatedType resolveReturnType(AnnotatedType enclosingType) {
        return ClassUtils.getReturnType(delegate, enclosingType);
    }

    /**
     * {@inheritDoc}
     *
     * @see java.lang.reflect.Executable#getParameterCount
     */
    @Override
    public int getParameterCount() {
        return this.delegate.getParameterCount();
    }

    @Override
    public AnnotatedType[] getAnnotatedParameterTypes() {
        return ClassUtils.getParameterTypes(delegate, enclosingType);
    }

    @Override
    public Parameter[] getParameters() {
        return delegate.getParameters();
    }
}
