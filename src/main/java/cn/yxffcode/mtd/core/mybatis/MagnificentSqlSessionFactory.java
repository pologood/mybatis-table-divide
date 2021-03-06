package cn.yxffcode.mtd.core.mybatis;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 用于创建{@link MagnificentSqlSession},取代{@link org.apache.ibatis.session.defaults.DefaultSqlSessionFactory}
 *
 * @author gaohang on 16/2/29.
 */
public class MagnificentSqlSessionFactory implements SqlSessionFactory {

  private final boolean listenerSupported;
  private final boolean multiTableSupported;
  private final Configuration configuration;
  private final cn.yxffcode.mtd.config.Configuration multiMybatisConfiguration =
      cn.yxffcode.mtd.config.Configuration.getInstance();

  public MagnificentSqlSessionFactory(boolean listenerSupported, boolean multiTableSupported,
                                      Configuration configuration) {
    this.listenerSupported = listenerSupported;
    this.multiTableSupported = multiTableSupported;
    this.configuration = configuration;
  }

  public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
  }

  public SqlSession openSession(boolean autoCommit) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
  }

  public SqlSession openSession(ExecutorType execType) {
    return openSessionFromDataSource(execType, null, false);
  }

  public SqlSession openSession(TransactionIsolationLevel level) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
  }

  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return openSessionFromDataSource(execType, level, false);
  }

  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return openSessionFromDataSource(execType, null, autoCommit);
  }

  public SqlSession openSession(Connection connection) {
    return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
  }

  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return openSessionFromConnection(execType, connection);
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  private SqlSession openSessionFromDataSource(ExecutorType execType,
                                               TransactionIsolationLevel level,
                                               boolean autoCommit) {
    Transaction tx = null;
    try {
      final Environment environment = configuration.getEnvironment();
      final TransactionFactory transactionFactory =
          getTransactionFactoryFromEnvironment(environment);
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      Executor internalExecutor = configuration.newExecutor(tx, execType, autoCommit);
      final Executor executor = multiTableSupported ?
          new MultiTableExecutor(internalExecutor, !listenerSupported) : internalExecutor;
      if (listenerSupported) {
        return new MagnificentSqlSession(configuration, executor,
            multiMybatisConfiguration.getListeners());
      }
      return new DefaultSqlSession(configuration, executor);
    } catch (Exception e) {
      closeTransaction(tx); // may have fetched a connection so lets call close()
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
    try {
      final Environment environment = configuration.getEnvironment();
      final TransactionFactory transactionFactory =
          getTransactionFactoryFromEnvironment(environment);
      final Transaction tx = transactionFactory.newTransaction(connection);
      Executor internalExecutor =
          configuration.newExecutor(tx, execType, connection.getAutoCommit());
      final Executor executor = multiTableSupported ?
          new MultiTableExecutor(internalExecutor, !listenerSupported) : internalExecutor;
      if (listenerSupported) {
        return new MagnificentSqlSession(configuration, executor,
            multiMybatisConfiguration.getListeners());
      }
      return new DefaultSqlSession(configuration, executor);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
    if (environment == null || environment.getTransactionFactory() == null) {
      return new ManagedTransactionFactory();
    }
    return environment.getTransactionFactory();
  }

  private void closeTransaction(Transaction tx) {
    if (tx != null) {
      try {
        tx.close();
      } catch (SQLException ignore) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

}
