/*
 * Copyright 2011-2019 the original author or authors.
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
package org.springframework.data.mongodb.config;

import static org.springframework.data.config.ParsingUtils.*;

import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.data.mongodb.core.MongoClientOptionsFactoryBean;
import org.springframework.data.mongodb.core.MongoClientSettingsFactoryBean;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Utility methods for {@link BeanDefinitionParser} implementations for MongoDB.
 *
 * @author Mark Pollack
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 */
@SuppressWarnings("deprecation")
abstract class MongoParsingUtils {

	private MongoParsingUtils() {}

	/**
	 * Parses the mongo replica-set element.
	 *
	 * @param element the mongo element.
	 * @param element the actual builder.
	 * @param mongoBuilder the bean definition builder to populate
	 * @return
	 */
	static void parseReplicaSet(Element element, BeanDefinitionBuilder mongoBuilder) {
		setPropertyValue(mongoBuilder, element, "replica-set", "replicaSetSeeds");
	}

	/**
	 * @param element
	 * @param mongoClientBuilder
	 * @return
	 * @since 2.3
	 */
	public static boolean parseMongoClientSettings(Element element, BeanDefinitionBuilder mongoClientBuilder) {

		Element settingsElement = DomUtils.getChildElementByTagName(element, "client-settings");
		if (settingsElement == null) {
			return false;
		}

		BeanDefinitionBuilder clientOptionsDefBuilder = BeanDefinitionBuilder
				.genericBeanDefinition(MongoClientSettingsFactoryBean.class);

		setPropertyValue(clientOptionsDefBuilder, settingsElement, "application-name", "applicationName");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "read-preference", "readPreference");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "read-concern", "readConcern");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "write-concern", "writeConcern");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "retry-reads", "retryReads");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "retry-writes", "retryWrites");

		// SocketSettings
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "socket-connect-timeout", "socketConnectTimeoutMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "socket-read-timeout", "socketReadTimeoutMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "socket-receive-buffer-size", "socketReceiveBufferSize");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "socket-send-buffer-size", "socketSendBufferSize");

		// Server Settings
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "server-heartbeat-frequency",
				"serverHeartbeatFrequencyMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "server-min-heartbeat-frequency",
				"serverMinHeartbeatFrequencyMS");

		// Cluster Settings
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-srv-host", "clusterSrvHost");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-hosts", "clusterHosts");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-connection-mode", "clusterConnectionMode");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-type", "custerRequiredClusterType");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-local-threshold", "clusterLocalThresholdMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-server-selection-timeout",
				"clusterServerSelectionTimeoutMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "cluster-max-wait-queue-size",
				"clusterMaxWaitQueueSize");

		// Connection Pool Settings
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-max-size", "poolMaxSize");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-min-size", "poolMinSize");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-max-wait-queue-size",
				"poolMaxWaitQueueSize");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-max-wait-time", "poolMaxWaitTimeMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-max-connection-life-time",
				"poolMaxConnectionLifeTimeMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-max-connection-idle-time",
				"poolMaxConnectionIdleTimeMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-maintenance-initial-delay",
				"poolMaintenanceInitialDelayMS");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "connection-pool-maintenance-frequency",
				"poolMaintenanceFrequencyMS");

		// SSL Settings
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "ssl-enabled", "sslEnabled");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "ssl-invalid-host-name-allowed",
				"sslInvalidHostNameAllowed");
		setPropertyValue(clientOptionsDefBuilder, settingsElement, "ssl-provider", "sslProvider");

		// Field level encryption
		setPropertyReference(clientOptionsDefBuilder, settingsElement, "encryption-settings-ref", "autoEncryptionSettings");

		// and the rest


		mongoClientBuilder.addPropertyValue("mongoClientSettings", clientOptionsDefBuilder.getBeanDefinition());

		return true;
	}

	/**
	 * Parses the {@code mongo:client-options} sub-element. Populates the given attribute factory with the proper
	 * attributes.
	 *
	 * @param element must not be {@literal null}.
	 * @param mongoClientBuilder must not be {@literal null}.
	 * @return
	 * @since 1.7
	 */
	public static boolean parseMongoClientOptions(Element element, BeanDefinitionBuilder mongoClientBuilder) {

		Element optionsElement = DomUtils.getChildElementByTagName(element, "client-options");

		if (optionsElement == null) {
			return false;
		}

		BeanDefinitionBuilder clientOptionsDefBuilder = BeanDefinitionBuilder
				.genericBeanDefinition(MongoClientOptionsFactoryBean.class);

		setPropertyValue(clientOptionsDefBuilder, optionsElement, "description", "description");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "min-connections-per-host", "minConnectionsPerHost");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "connections-per-host", "connectionsPerHost");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "threads-allowed-to-block-for-connection-multiplier",
				"threadsAllowedToBlockForConnectionMultiplier");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "max-wait-time", "maxWaitTime");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "max-connection-idle-time", "maxConnectionIdleTime");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "max-connection-life-time", "maxConnectionLifeTime");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "connect-timeout", "connectTimeout");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "socket-timeout", "socketTimeout");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "socket-keep-alive", "socketKeepAlive");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "read-preference", "readPreference");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "write-concern", "writeConcern");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "heartbeat-frequency", "heartbeatFrequency");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "min-heartbeat-frequency", "minHeartbeatFrequency");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "heartbeat-connect-timeout", "heartbeatConnectTimeout");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "heartbeat-socket-timeout", "heartbeatSocketTimeout");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "ssl", "ssl");
		setPropertyReference(clientOptionsDefBuilder, optionsElement, "ssl-socket-factory-ref", "sslSocketFactory");
		setPropertyReference(clientOptionsDefBuilder, optionsElement, "encryption-settings-ref", "autoEncryptionSettings");
		setPropertyValue(clientOptionsDefBuilder, optionsElement, "server-selection-timeout", "serverSelectionTimeout");

		mongoClientBuilder.addPropertyValue("mongoClientOptions", clientOptionsDefBuilder.getBeanDefinition());

		return true;
	}

	/**
	 * Returns the {@link BeanDefinitionBuilder} to build a {@link BeanDefinition} for a
	 * {@link WriteConcernPropertyEditor}.
	 *
	 * @return
	 */
	static BeanDefinitionBuilder getWriteConcernPropertyEditorBuilder() {

		Map<String, Class<?>> customEditors = new ManagedMap<String, Class<?>>();
		customEditors.put("com.mongodb.WriteConcern", WriteConcernPropertyEditor.class);

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);

		return builder;
	}

	/**
	 * Returns the {@link BeanDefinitionBuilder} to build a {@link BeanDefinition} for a
	 * {@link ReadConcernPropertyEditor}.
	 *
	 * @return
	 * @since 2.3
	 */
	static BeanDefinitionBuilder getReadConcernPropertyEditorBuilder() {

		Map<String, Class<?>> customEditors = new ManagedMap<String, Class<?>>();
		customEditors.put("com.mongodb.ReadConcern", ReadConcernPropertyEditor.class);

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);

		return builder;
	}

	/**
	 * One should only register one bean definition but want to have the convenience of using
	 * AbstractSingleBeanDefinitionParser but have the side effect of registering a 'default' property editor with the
	 * container.
	 */
	static BeanDefinitionBuilder getServerAddressPropertyEditorBuilder() {

		Map<String, String> customEditors = new ManagedMap<String, String>();
		customEditors.put("com.mongodb.ServerAddress[]",
				"org.springframework.data.mongodb.config.ServerAddressPropertyEditor");

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);
		return builder;
	}

	/**
	 * Returns the {@link BeanDefinitionBuilder} to build a {@link BeanDefinition} for a
	 * {@link ReadPreferencePropertyEditor}.
	 *
	 * @return
	 * @since 1.7
	 */
	static BeanDefinitionBuilder getReadPreferencePropertyEditorBuilder() {

		Map<String, Class<?>> customEditors = new ManagedMap<String, Class<?>>();
		customEditors.put("com.mongodb.ReadPreference", ReadPreferencePropertyEditor.class);

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);

		return builder;
	}

	/**
	 * Returns the {@link BeanDefinitionBuilder} to build a {@link BeanDefinition} for a
	 * {@link MongoCredentialPropertyEditor}.
	 *
	 * @return
	 * @since 1.7
	 */
	static BeanDefinitionBuilder getMongoCredentialPropertyEditor() {

		Map<String, Class<?>> customEditors = new ManagedMap<String, Class<?>>();
		customEditors.put("com.mongodb.MongoCredential[]", MongoCredentialPropertyEditor.class);

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);

		return builder;
	}

	/**
	 * Returns the {@link BeanDefinitionBuilder} to build a {@link BeanDefinition} for a
	 * {@link ConnectionStringPropertyEditor}.
	 *
	 * @return
	 * @since 2.3
	 */
	static BeanDefinitionBuilder getConnectionStringPropertyEditorBuilder() {

		Map<String, Class<?>> customEditors = new ManagedMap<String, Class<?>>();
		customEditors.put("com.mongodb.ConnectionString", ConnectionStringPropertyEditor.class);

		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CustomEditorConfigurer.class);
		builder.addPropertyValue("customEditors", customEditors);

		return builder;
	}


}
