/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoClientSettings.Builder;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;

/**
 * Convenient factory for configuring MongoDB.
 *
 * @author Christoph Strobl
 * @since 2.3
 */
public class MongoClientClientFactoryBean extends AbstractFactoryBean<MongoClient>
		implements PersistenceExceptionTranslator {

	private static final PersistenceExceptionTranslator DEFAULT_EXCEPTION_TRANSLATOR = new MongoExceptionTranslator();

	private @Nullable MongoClientSettings mongoClientSettings;
	private @Nullable String host;
	private @Nullable Integer port;
	private @Nullable List<MongoCredential> credential = null;
	private @Nullable ConnectionString connectionString;
	private @Nullable String replicaSet = null;

	private PersistenceExceptionTranslator exceptionTranslator = DEFAULT_EXCEPTION_TRANSLATOR;

	/**
	 * Set the {@link MongoClientSettings} to be used when creating {@link MongoClient}.
	 *
	 * @param mongoClientOptions
	 */
	public void setMongoClientSettings(@Nullable MongoClientSettings mongoClientOptions) {
		this.mongoClientSettings = mongoClientOptions;
	}

	/**
	 * Set the list of credentials to be used when creating {@link MongoClient}.
	 *
	 * @param credential can be {@literal null}.
	 */
	public void setCredential(@Nullable MongoCredential[] credential) {
		this.credential = Arrays.asList(credential);
	}

	/**
	 * Configures the host to connect to.
	 *
	 * @param host
	 */
	public void setHost(@Nullable String host) {
		this.host = host;
	}

	/**
	 * Configures the port to connect to.
	 *
	 * @param port
	 */
	public void setPort(int port) {
		this.port = port;
	}

	public void setConnectionString(@Nullable ConnectionString connectionString) {
		this.connectionString = connectionString;
	}

	public void setReplicaSet(@Nullable String replicaSet) {
		this.replicaSet = replicaSet;
	}

	/**
	 * Configures the {@link PersistenceExceptionTranslator} to use.
	 *
	 * @param exceptionTranslator
	 */
	public void setExceptionTranslator(@Nullable PersistenceExceptionTranslator exceptionTranslator) {
		this.exceptionTranslator = exceptionTranslator == null ? DEFAULT_EXCEPTION_TRANSLATOR : exceptionTranslator;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 */
	public Class<? extends MongoClient> getObjectType() {
		return MongoClient.class;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.dao.support.PersistenceExceptionTranslator#translateExceptionIfPossible(java.lang.RuntimeException)
	 */
	@Nullable
	public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
		return exceptionTranslator.translateExceptionIfPossible(ex);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.AbstractFactoryBean#createInstance()
	 */
	@Override
	protected MongoClient createInstance() throws Exception {

		ConnectionString connectionString = this.connectionString != null ? this.connectionString
				: new ConnectionString(String.format("mongodb://%s:%s", getOrDefault(host, ServerAddress.defaultHost()),
						getOrDefault(port, "" + ServerAddress.defaultPort())));
		Builder builder = MongoClientSettings.builder().applyConnectionString(connectionString);
		if (mongoClientSettings != null) {

			SslSettings sslSettings = mongoClientSettings.getSslSettings();
			ClusterSettings clusterSettings = mongoClientSettings.getClusterSettings();
			ConnectionPoolSettings connectionPoolSettings = mongoClientSettings.getConnectionPoolSettings();
			SocketSettings socketSettings = mongoClientSettings.getSocketSettings();
			ServerSettings serverSettings = mongoClientSettings.getServerSettings();

			builder = builder //
					.applicationName(mongoClientSettings.getApplicationName()) //
					.applyToSslSettings(settings -> settings.applySettings(sslSettings))
					.applyToClusterSettings(settings -> settings.applySettings(clusterSettings)) //
					.applyToConnectionPoolSettings(settings -> settings.applySettings(connectionPoolSettings)) //
					.applyToSocketSettings(settings -> settings.applySettings(socketSettings)) //
					.applyToServerSettings(settings -> settings.applySettings(serverSettings)) //
					.autoEncryptionSettings(mongoClientSettings.getAutoEncryptionSettings()) //
					.codecRegistry(mongoClientSettings.getCodecRegistry()) //
					.readConcern(mongoClientSettings.getReadConcern()) //
					.readPreference(mongoClientSettings.getReadPreference()) //
					.retryReads(mongoClientSettings.getRetryReads()) //
					.retryWrites(mongoClientSettings.getRetryWrites()) //
					.writeConcern(mongoClientSettings.getWriteConcern());
		}

		if (!CollectionUtils.isEmpty(credential)) {
			builder = builder.credential(credential.iterator().next());
		}

		if (StringUtils.hasText(replicaSet)) {
			builder.applyToClusterSettings((settings) -> {
				settings.requiredReplicaSetName(replicaSet);
			});
		}

		return createMongoClient(builder.build());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.AbstractFactoryBean#destroyInstance(java.lang.Object)
	 */
	@Override
	protected void destroyInstance(@Nullable MongoClient instance) throws Exception {

		if (instance != null) {
			instance.close();
		}
	}

	private MongoClient createMongoClient(MongoClientSettings settings) throws UnknownHostException {
		return MongoClients.create(settings);
	}

	private String getOrDefault(Object value, String defaultValue) {
		return !StringUtils.isEmpty(value) ? value.toString() : defaultValue;
	}
}
