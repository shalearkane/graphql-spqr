package io.leangen.graphql.execution;

import graphql.GraphQLException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.LightDataFetcher;
import io.leangen.graphql.generator.mapping.ArgumentInjector;
import io.leangen.graphql.generator.mapping.ConverterRegistry;
import io.leangen.graphql.generator.mapping.DelegatingOutputConverter;
import io.leangen.graphql.metadata.Operation;
import io.leangen.graphql.metadata.Resolver;
import io.leangen.graphql.metadata.strategy.value.ValueMapper;
import io.leangen.graphql.util.Utils;
import org.dataloader.BatchLoaderEnvironment;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by bojan.tomic on 1/29/17.
 */
public class OperationLightExecutor extends OperationExecutor implements LightDataFetcher<Object> {

    private final Operation operation;
    private final ValueMapper valueMapper;
    private final GlobalEnvironment globalEnvironment;
    private final ConverterRegistry converterRegistry;
    private final DerivedTypeRegistry derivedTypes;
    private final Map<Resolver, List<ResolverInterceptor>> innerInterceptors;
    private final Map<Resolver, List<ResolverInterceptor>> outerInterceptors;

    public OperationLightExecutor(Operation operation, ValueMapper valueMapper, GlobalEnvironment globalEnvironment, ResolverInterceptorFactory interceptorFactory) {
        super(operation, valueMapper, globalEnvironment, interceptorFactory);
        this.operation = operation;
        this.valueMapper = valueMapper;
        this.globalEnvironment = globalEnvironment;
        this.converterRegistry = optimizeConverters(operation.getResolvers(), globalEnvironment.converters);
        this.derivedTypes = deriveTypes(operation.getResolvers(), converterRegistry);
        this.innerInterceptors = operation.getResolvers().stream().collect(Collectors.toMap(Function.identity(),
                res -> interceptorFactory.getInterceptors(new ResolverInterceptorFactoryParams(res))));
        this.outerInterceptors = operation.getResolvers().stream().collect(Collectors.toMap(Function.identity(),
                res -> interceptorFactory.getOuterInterceptors(new ResolverInterceptorFactoryParams(res))));
    }

    @Override
    public Object get(GraphQLFieldDefinition fieldDefinition, Object sourceObject, Supplier<DataFetchingEnvironment> env) throws Exception {
        Map<String, Object> NO_ARGUMENTS = new HashMap<>();
        Resolver resolver = this.operation.getApplicableResolver(NO_ARGUMENTS.keySet());
        if (resolver == null) {
            throw new GraphQLException("Resolver for operation " + operation.getName() + " accepting arguments: "
                    + NO_ARGUMENTS.keySet() + " not implemented");
        }
        ResolutionEnvironment resolutionEnvironment = new ResolutionEnvironment(resolver, env, this.valueMapper, this.globalEnvironment, this.converterRegistry, this.derivedTypes);
        return execute(resolver, resolutionEnvironment, sourceObject);
    }

    @Override
    public Object get(DataFetchingEnvironment env) throws Exception {
       return null;
    }

    public Object execute(List<Object> keys, Map<String, Object> arguments, BatchLoaderEnvironment env) throws Exception {
        Resolver resolver = this.operation.getApplicableResolver(arguments.keySet());
        if (resolver == null) {
            throw new GraphQLException("Batch loader for operation " + operation.getName() + " not implemented");
        }
        ResolutionEnvironment resolutionEnvironment = new ResolutionEnvironment(resolver, keys, env, this.valueMapper, this.globalEnvironment, this.converterRegistry, this.derivedTypes);
        return execute(resolver, resolutionEnvironment, arguments);
    }

    /**
     * Prepares input arguments by calling respective {@link ArgumentInjector}s
     * and invokes the underlying resolver method/field
     *
     * @param resolver The resolver to be invoked once the arguments are prepared
     * @param resolutionEnvironment An object containing all contextual information needed during operation resolution
     * @param sourceObject Object of which the method is being called
     *
     * @return The result returned by the underlying method/field, potentially proxied and wrapped
     *
     * @throws Exception If the invocation of the underlying method/field or any of the interceptors throws
     */
    private Object execute(Resolver resolver, ResolutionEnvironment resolutionEnvironment, Object sourceObject)
            throws Exception {
        final Object[] NO_ARGUMENTS = new Object[]{};
        InvocationContext invocationContext = new InvocationContext(operation, resolver, resolutionEnvironment, NO_ARGUMENTS);
        Queue<ResolverInterceptor> interceptors = new ArrayDeque<>(this.outerInterceptors.get(resolver));
        interceptors.add((ctx, cont) -> resolutionEnvironment.convertOutput(cont.proceed(ctx), resolver.getTypedElement(), resolver.getReturnType()));
        interceptors.addAll(this.innerInterceptors.get(resolver));
        interceptors.add((ctx, cont) -> {
            try {
                return resolver.resolve(sourceObject, NO_ARGUMENTS);
            } catch (ReflectiveOperationException e) {
                sneakyThrow(unwrap(e));
            }
            return null; //never happens, needed because of sneakyThrow
        });
        return execute(invocationContext, interceptors);
    }

    private Object execute(InvocationContext context, Queue<ResolverInterceptor> interceptors) throws Exception {
        return interceptors.remove().aroundInvoke(context, (ctx) -> execute(ctx, interceptors));
    }

    private ConverterRegistry optimizeConverters(Collection<Resolver> resolvers, ConverterRegistry converters) {
        return converters.optimize(resolvers.stream().map(Resolver::getTypedElement).collect(Collectors.toList()));
    }

    private DerivedTypeRegistry deriveTypes(Collection<Resolver> resolvers, ConverterRegistry converterRegistry) {
        return new DerivedTypeRegistry(
                resolvers.stream().map(Resolver::getTypedElement).collect(Collectors.toList()),
                Utils.extractInstances(converterRegistry.getOutputConverters(), DelegatingOutputConverter.class)
                        .collect(Collectors.toList()));
    }

    private Throwable unwrap(ReflectiveOperationException e) {
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return cause;
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    public Operation getOperation() {
        return operation;
    }
}
