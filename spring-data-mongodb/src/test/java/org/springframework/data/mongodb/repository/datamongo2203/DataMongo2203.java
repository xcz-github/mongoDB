/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.data.mongodb.repository.datamongo2203;

import java.util.UUID;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.Data;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Christoph Strobl
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class DataMongo2203 {

	@Configuration
	@EnableMongoRepositories(considerNestedRepositories = true)
	static class Config extends AbstractMongoClientConfiguration {

		@Override
		public MongoClient mongoClient() {
			return MongoClients.create();
		}

		@Override
		protected String getDatabaseName() {
			return "datamongo-2203";
		}
	}
	@Autowired Repo repo;
	@Autowired MongoTemplate template;

	@Before
	public void beforeEach() {
		template.dropCollection(Person.class);
	}

	@Test
	public void shouldFindByUuidViaDerivedFinder() {

		UUID uuid = UUID.fromString("c04dd3f3-b2b0-2f52-8cee-b22f8889d4a1");

		Person source = new Person();
		source.id = "id-1";
		source.uuid = uuid;

		repo.save(source);

		Person target = repo.findByUuid(uuid);
		Assertions.assertThat(target).isEqualTo(source);
	}

	@Test
	public void shouldFindByUuidViaAnnotatedQuery() {

		UUID uuid = UUID.fromString("c04dd3f3-b2b0-2f52-8cee-b22f8889d4a1");

		Person source = new Person();
		source.id = "id-1";
		source.uuid = uuid;

		repo.save(source);

		Person target = repo.findPersonByUuid(uuid);
		Assertions.assertThat(target).isEqualTo(source);
	}


	interface Repo extends CrudRepository<Person, String> {

		Person findByUuid(UUID uuid);

		@Query("{ 'uuid' : { '$eq' : ?0 } }")
		Person findPersonByUuid(UUID uuid);
	}

	@Data
	static class Person {
		@Id String id;
		UUID uuid;
	}
}
