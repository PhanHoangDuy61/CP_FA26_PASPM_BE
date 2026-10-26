package vn.edu.fpt.paspm.recommendation;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Test-only actual JDBC execute counter: no parameter values, credentials or SQL are retained. */
public final class RecommendationSqlCounter implements BeanPostProcessor {
    private boolean enabled;
    private final Map<String,Integer> queries = new TreeMap<>();
    public synchronized void start() { queries.clear(); enabled = true; }
    public synchronized Map<String,Integer> stop() { enabled = false; return Map.copyOf(queries); }
    private synchronized void count(String sql) {
        if (!enabled || sql == null || !sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) return;
        var matcher = java.util.regex.Pattern.compile("(?i)\\bFROM\\s+([a-z_]+)").matcher(sql);
        String table = matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "other";
        queries.merge(table, 1, Integer::sum);
    }
    @Override public Object postProcessAfterInitialization(Object bean,String name) throws BeansException {
        if (!(bean instanceof DataSource delegate)) return bean;
        return new DelegatingDataSource(delegate) {
            @Override public Connection getConnection() throws SQLException { return connection(delegate.getConnection()); }
            @Override public Connection getConnection(String user,String password) throws SQLException { return connection(delegate.getConnection(user,password)); }
        };
    }
    private Connection connection(Connection delegate) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)-> {
            Object result=invoke(method,delegate,args);
            if (result instanceof PreparedStatement statement && args != null && args.length>0 && args[0] instanceof String sql)
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)-> {
                    if (m.getName().startsWith("execute")) count(sql);
                    return invoke(m,statement,a);
                });
            return result;
        });
    }
    private static Object invoke(Method method,Object delegate,Object[] args) throws Throwable {
        try { return method.invoke(delegate,args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }
}
