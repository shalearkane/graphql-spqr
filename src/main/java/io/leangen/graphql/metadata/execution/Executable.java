package io.leangen.graphql.metadata.execution;

import java.lang.reflect.*;

public abstract class Executable<T extends AnnotatedElement & Member> {

    T delegate;

    abstract public Object execute(Object target, Object[] args) throws InvocationTargetException, IllegalAccessException;

    abstract public AnnotatedType getReturnType();

    /**
     * Returns the number of formal parameters this executable takes.
     * May differ from {@code getParameters().length}.
     *
     * @return The number of formal parameter this executable takes
     * */
    abstract public int getParameterCount();

    abstract public AnnotatedType[] getAnnotatedParameterTypes();

    abstract public Parameter[] getParameters();

    public T getDelegate() {
        return delegate;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    /**
     * Two {@code Executable}s are considered equal either if their wrapped fields/methods are equal
     * or if one wraps a field and the other its corresponding getter/setter.
     * 
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object that) {
        return this == that || (that instanceof Executable && ((Executable) that).delegate.equals(this.delegate));
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
