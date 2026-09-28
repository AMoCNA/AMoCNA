package com.kubiki.daedalus.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.*;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.TemplateParser;
import com.kubiki.daedalus.core.TemplateToken;
import com.kubiki.daedalus.exception.DaedalusException;
import com.kubiki.daedalus.exception.HydrationException;
import com.kubiki.daedalus.exception.TemplateMappingException;
import com.kubiki.daedalus.exception.TemplateResourceException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kubiki.daedalus.core.DaedalusConstants.*;

public class DaedalusInvocationHandler implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(DaedalusInvocationHandler.class);

    private final Class<?> interfaceClass;
    private final Map<Method, List<TemplateToken>> methodTemplates = new HashMap<>();
    private final Map<Method, Boolean> isUpdate = new HashMap<>();
    private final GlobalTemplateContext globalContext;
    private final Formatter formatter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Repository repository;

    public DaedalusInvocationHandler(Class<?> interfaceClass, GlobalTemplateContext globalContext, Formatter formatter, Repository repository) {
        this.interfaceClass = interfaceClass;
        this.globalContext = globalContext;
        this.formatter = formatter;
        this.repository = repository;
        TemplateParser parser = new TemplateParser();
        for (Method method : interfaceClass.getMethods()) {
            String raw = null;
            Template templateAnn = method.getAnnotation(Template.class);
            if (templateAnn != null) {
                raw = loadResource(templateAnn.resource());
            }

            SparqlQuery queryAnn = method.getAnnotation(SparqlQuery.class);
            if (queryAnn != null) {
                raw = queryAnn.value().isEmpty() ? loadResource(queryAnn.resource()) : queryAnn.value();
            }

            SparqlUpdate updateAnn = method.getAnnotation(SparqlUpdate.class);
            if (updateAnn != null) {
                raw = updateAnn.value().isEmpty() ? loadResource(updateAnn.resource()) : updateAnn.value();
                isUpdate.put(method, true);
            }

            if (raw != null) {
                methodTemplates.put(method, parser.parse(raw));
            }
        }
    }

    private String loadResource(String path) {
        try (var is = interfaceClass.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new TemplateResourceException("Resource not found: " + path);
            return new String(is.readAllBytes());
        } catch (Exception e) {
            throw new TemplateResourceException("Failed to load template: " + path, e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<TemplateToken> tokens = methodTemplates.get(method);
        if (tokens == null) {
            switch (method.getName()) {
                case EQUALS_METHOD -> {
                    if (args[0] == null) return false;
                    if (!Proxy.isProxyClass(args[0].getClass())) return false;
                    InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
                    return this.equals(handler);
                }
                case HASH_CODE_METHOD -> {
                    return System.identityHashCode(proxy);
                }
                case TO_STRING_METHOD -> {
                    return PROXY_PREFIX + interfaceClass.getSimpleName() + PROXY_SUFFIX;
                }
            }
            throw new DaedalusException("Method not annotated with @Template, @SparqlQuery or @SparqlUpdate: " + method.getName());
        }

        Map<String, String> values = new HashMap<>(globalContext.getAll());
        Parameter[] params = method.getParameters();
        if (args != null) {
            for (int i = 0; i < params.length; i++) {
                Bind bind = params[i].getAnnotation(Bind.class);
                if (bind != null) {
                    Type typeAnn = params[i].getAnnotation(Type.class);
                    TemplateType type = (typeAnn != null) ? typeAnn.value() : TemplateType.LITERAL;
                    values.put(bind.value(), formatter.format(args[i], type));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (TemplateToken token : tokens) {
            if (token instanceof TemplateToken.StaticToken(String text)) {
                sb.append(text);
            } else if (token instanceof TemplateToken.VariableToken(String name, String defaultValue)) {
                String val = values.get(name);
                if (val == null) {
                    val = defaultValue;
                }
                if (val == null) {
                    throw new HydrationException("Missing variable: " + name + " for method: " + method.getName());
                }
                sb.append(val);
            }
        }

        String hydrated = sb.toString();

        if (Boolean.TRUE.equals(isUpdate.get(method))) {
            if (repository == null) {
                throw new DaedalusException("Cannot execute @SparqlUpdate: Repository bean not found in context");
            }
            log.debug("Executing SPARQL UPDATE:\n{}", hydrated);
            try (RepositoryConnection conn = repository.getConnection()) {
                conn.prepareUpdate(hydrated).execute();
            }
            return null;
        }

        if (method.isAnnotationPresent(SparqlQuery.class)) {
            if (repository == null) {
                throw new DaedalusException("Cannot execute @SparqlQuery: Repository bean not found in context");
            }
            log.debug("Executing SPARQL QUERY:\n{}", hydrated);
            try (RepositoryConnection conn = repository.getConnection()) {
                if (method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(boolean.class)) {
                    return conn.prepareBooleanQuery(hydrated).evaluate();
                }

                try (TupleQueryResult result = conn.prepareTupleQuery(hydrated).evaluate()) {
                    if (method.getReturnType().equals(List.class)) {
                        return result.stream().toList();
                    }
                    // Add more mapping logic if needed, for now return list of binding sets
                    return result.stream().toList();
                }
            }
        }

        if (method.getReturnType().equals(String.class)) return hydrated;
        try {
            return objectMapper.readValue(hydrated, method.getReturnType());
        } catch (Exception e) {
            throw new TemplateMappingException("Failed to map hydrated template to " + method.getReturnType().getSimpleName(), e);
        }
    }
}
